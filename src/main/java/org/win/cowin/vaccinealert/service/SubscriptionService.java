package org.win.cowin.vaccinealert.service;

import com.auth0.jwt.JWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.win.cowin.vaccinealert.dto.District;
import org.win.cowin.vaccinealert.dto.Districts;
import org.win.cowin.vaccinealert.dto.State;
import org.win.cowin.vaccinealert.dto.States;
import org.win.cowin.vaccinealert.dto.Subscription;
import org.win.cowin.vaccinealert.dto.SubscriptionDetails;
import org.win.cowin.vaccinealert.dto.UserSubscription;

@Service
@Slf4j
public class SubscriptionService {

  static Map<Integer, Boolean> districtMap = new HashMap<>();

  private final Map<Integer, List<Subscription>> subscriptions = new ConcurrentHashMap<>();

  private final Map<String, List<Integer>> subscriptionsInverse = new ConcurrentHashMap<>();

  @Autowired
  private CryptoService cryptoService;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private RestTemplate restTemplate;

  @Value("${cowin.districts.url}")
  private String districtUrl;

  @Value("${cowin.states.url}")
  private String stateUrl;

  public Map<Integer, Boolean> getDistrictMap() {
    return districtMap;
  }

  private void setDistrictMap(Map<Integer, Boolean> districtList) {
    districtMap = districtList;
  }

  public List<Subscription> getSubscriptions(Integer district) {
    return this.subscriptions.get(district);
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  /**
   * @return true if the subscription is no longer valid and can be removed,
   * false if everything is okay
   */
  public boolean sendPushMessage(Subscription subscription, byte[] body) throws JsonProcessingException {
    String origin = null;
    try {
      URL url = new URL(subscription.getEndpoint());
      origin = url.getProtocol() + "://" + url.getHost();
    } catch (MalformedURLException e) {
      log.error("{}", e.getMessage());
    }

    Date today = new Date();
    Date expires = new Date(today.getTime() + 12 * 60 * 60 * 1000);

    String token = JWT.create().withAudience(origin).withExpiresAt(expires)
        .withSubject("mailto:example@example.com").sign(this.cryptoService.getJwtAlgorithm());

    HttpHeaders headers = new HttpHeaders();
    headers.set("TTL", "180");
    headers.set("Content-Type", "application/octet-stream");
    headers.set("Content-Encoding", "aes128gcm");
    headers.set("Content-Length", String.valueOf(body.length));
    headers.set("Authorization", "vapid t=" + token + ", k=" + this.cryptoService.getPublicKeyBase64());
    HttpEntity<byte[]> request = new HttpEntity<>(body, headers);

    ResponseEntity<String> responseEntity = restTemplate.postForEntity(subscription.getEndpoint(), request, String.class);
    switch (responseEntity.getStatusCode().value()) {
      case 201:
        log.info("Push message successfully sent: {}", subscription.getEndpoint());
        break;
      case 404:
      case 410:
        log.warn("Subscription not found or gone: {}", subscription.getEndpoint());
        // remove subscription from our collection of subscriptions
        return true;
      case 429:
        log.error("Too many requests: {}", request);
        break;
      case 400:
        log.error("Invalid request: {}", request);
        break;
      case 413:
        log.error("Payload size too large: {}", request);
        break;
      default:
        log.error("Unhandled status code: {} / {}", responseEntity.getStatusCode(), request);
    }

    return false;
  }

  public void ingest(UserSubscription userSubscription) {
    if (districtMap.get(userSubscription.getDistrict()) == null) {
      return;
    }
    districtMap.put(userSubscription.getDistrict(), Boolean.TRUE);
    List<Subscription> subscriptionList = this.subscriptions.get(userSubscription.getDistrict());
    if (subscriptionList == null) {
      subscriptionList = new ArrayList<>();
    }
    subscriptionList.add(userSubscription.getSubscription());
    this.subscriptions.put(userSubscription.getDistrict(), subscriptionList);
    List<Integer> districtList = this.subscriptionsInverse.get(userSubscription.getSubscription().getEndpoint());
    if (districtList == null) {
      districtList = new ArrayList<>();
    }
    districtList.add(userSubscription.getDistrict());
    this.subscriptionsInverse.put(userSubscription.getSubscription().getEndpoint(), districtList);
  }

  //Brute force approach. To be optimized
  public void remove(String subscriptionEndpoint) {
    List<Integer> districtList = this.subscriptionsInverse.get(subscriptionEndpoint);
    for (Integer district : districtList) {
      List<Subscription> subscriptionList = this.subscriptions.get(district);
      Iterator<Subscription> subscriptionIterator = subscriptionList.listIterator();
      while (subscriptionIterator.hasNext()) {
        Subscription subscription = subscriptionIterator.next();
        if (subscription.getEndpoint().equals(subscriptionEndpoint)) {
          subscriptionIterator.remove();
        }
      }
      if (subscriptionList.isEmpty()) {
        districtMap.put(district, Boolean.FALSE);
      }
    }
    this.subscriptionsInverse.remove(subscriptionEndpoint);
  }

  public boolean contains(SubscriptionDetails subscriptionDetails) {
    return this.subscriptionsInverse.containsKey(subscriptionDetails.getEndpoint());
  }

  @PostConstruct
  public void initDistricts() {
    log.info("inside initDistricts");
    Map<Integer, Boolean> districtMap = new HashMap<>();
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
    headers.add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.99 Safari/537.36");
    HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);
    ResponseEntity<States> stateResponseEntity = restTemplate.exchange(stateUrl, HttpMethod.GET, entity, States.class);

    //States states = restTemplate.getForObject(stateUrl, States.class);
    if (stateResponseEntity != null && stateResponseEntity.getBody() != null) {
      for (State state : stateResponseEntity.getBody().getStates()) {
        log.info("for state {}", state.getState_name());
        ResponseEntity<Districts> districtsResponseEntity = restTemplate.exchange(districtUrl + state.getState_id(), HttpMethod.GET, entity, Districts.class);
        if (districtsResponseEntity != null && districtsResponseEntity.getBody() != null) {
          for (District district : districtsResponseEntity.getBody().getDistricts()) {
            districtMap.put(district.getDistrict_id(), Boolean.FALSE);
          }
        }
      }
    }
    log.info("districtmap has {} elements", districtMap.keySet().size());
    this.setDistrictMap(districtMap);
  }

}
