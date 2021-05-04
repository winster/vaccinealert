package org.win.cowin.vaccinealert.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.win.cowin.vaccinealert.dto.Center;
import org.win.cowin.vaccinealert.dto.Centers;
import org.win.cowin.vaccinealert.dto.PushMessage;
import org.win.cowin.vaccinealert.dto.Session;
import org.win.cowin.vaccinealert.dto.Subscription;

@Service
@Slf4j
public class AlertScheduler {

  @Value("${cowin.search.url}")
  private String searchUrl;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  private SubscriptionService subscriptionService;

  @Autowired
  private CryptoService cryptoService;

  @Autowired
  private ObjectMapper objectMapper;

  @Scheduled(cron = "0 0/1 21-23 * * ?")
  public void searchForVaccine() {
    log.info("searchForVaccine :: {}", new SimpleDateFormat("dd-MM-yyyy").format(new Date()));
    Map<Integer, Boolean> districtMap = subscriptionService.getDistrictMap();
    boolean alert = false;
    String centerName = null;
    for (Integer district : districtMap.keySet()) {
      if (districtMap.get(district)) {
        Centers centers = restTemplate.getForObject(searchUrl + "district_id=" + district + "&date=" + new SimpleDateFormat("dd-MM-yyyy").format(new Date()), Centers.class);
        log.info("search for {} found {}", district, centers.getCenters().size());
        alert = false;
        if (centers != null) {
          for (Center center : centers.getCenters()) {
            if (center.getSessions() != null) {
              for (Session session : center.getSessions()) {
                if (session.getAvailable_capacity() > 0) {
                  log.info("Found availability at {}", center.getName());
                  centerName = center.getName();
                  alert = true;
                  break;
                }
              }
              if (alert) {
                break;
              }
            }
          }
        }
        if (alert) {
          notifySubscribers(district, centerName);
        }
      }
    }
    return;
  }

  private void notifySubscribers(Integer district, String centerName) {
    log.info("inside notifyUsers");
    Set<String> failedSubscriptions = new HashSet<>();

    List<Subscription> subscriptionList = subscriptionService.getSubscriptions(district);
    if (subscriptionList == null) {
      return;
    }
    for (Subscription subscription : subscriptionList) {
      try {
        log.info("for subscription {}", subscription);
        byte[] result = this.cryptoService.encrypt(
            this.objectMapper.writeValueAsString(new PushMessage("Vaccine Ready", String.format("%s now open for appointment.", centerName))),
            subscription.getKeys().getP256dh(),
            subscription.getKeys().getAuth(), 0);
        boolean remove = subscriptionService.sendPushMessage(subscription, result);
        if (remove) {
          failedSubscriptions.add(subscription.getEndpoint());
        }
      } catch (InvalidKeyException | NoSuchAlgorithmException | InvalidAlgorithmParameterException | IllegalStateException | InvalidKeySpecException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | JsonProcessingException e) {
        log.error("send encrypted message", e);
      }
    }
    failedSubscriptions.forEach(subscriptionService::remove);
  }
}
