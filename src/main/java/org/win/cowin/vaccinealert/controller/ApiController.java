package org.win.cowin.vaccinealert.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.win.cowin.vaccinealert.dto.SubscriptionDetails;
import org.win.cowin.vaccinealert.dto.UserSubscription;
import org.win.cowin.vaccinealert.service.CryptoService;
import org.win.cowin.vaccinealert.service.SubscriptionService;

@RestController
@RequestMapping("/api")
@Slf4j
public class ApiController {

  private final CryptoService cryptoService;

  private final SubscriptionService subscriptionService;

  public ApiController(CryptoService cryptoService, SubscriptionService subscriptionService) {
    this.cryptoService = cryptoService;
    this.subscriptionService = subscriptionService;
  }

  @GetMapping(path = "/publicSigningKey", produces = "application/octet-stream")
  public byte[] publicSigningKey() {
    return this.cryptoService.getPublicKeyUncompressed();
  }

  @PostMapping("/subscribe")
  @ResponseStatus(HttpStatus.CREATED)
  public void subscribe(@RequestBody UserSubscription userSubscription) {
    this.subscriptionService.ingest(userSubscription);
    log.info("subscribe {}", userSubscription);
  }

  @PostMapping("/unsubscribe")
  public void unsubscribe(@RequestBody SubscriptionDetails subscription) {
    this.subscriptionService.remove(subscription.getEndpoint());
    log.info("unsubscribe {}", subscription);
  }

  @PostMapping("/isSubscribed")
  public boolean isSubscribed(@RequestBody SubscriptionDetails subscription) {
    return this.subscriptionService.contains(subscription);
  }


}
