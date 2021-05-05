const stateSelect = document.getElementById('stateSelect');
const districtSelect = document.getElementById('districtSelect');
const subscribeButton = document.getElementById('subscribeButton');
const unsubscribeButton = document.getElementById('unsubscribeButton');
const previousSubscriptionsDiv = document.getElementById('previousSubscriptionsDiv');
const previousAlertsDiv = document.getElementById('previousAlertsDiv');

if ("serviceWorker" in navigator) {
  try {
	checkSubscription();
    init();
  } catch (e) {
    console.error('error init(): ' + e);
  }

  subscribeButton.addEventListener('click', () => {
	  subscribe().catch(e => {
		  if (Notification.permission === 'denied') {
	         console.warn('Permission for notifications was denied');
	      } else {
	    	 console.error('error subscribe(): ' + e);
	      }
	  });
  });

  unsubscribeButton.addEventListener('click', () => {
	unsubscribe().catch(e => console.error('error unsubscribe(): ' + e));
  });

  showStates();

  stateSelect.addEventListener("change", function() {
    showDistricts(stateSelect.value)
  });

}

window.addEventListener("load", function() {
    if(window.location.hash === '#alert') {
        unsubscribe();
    }
})

async function showStates() {
    const response = await fetch("https://cdn-api.co-vin.in/api/v2/admin/location/states");
    const result = await response.json();
    stateSelect.add(new Option("Select a state", ""));
    result.states.forEach(state => stateSelect.add(new Option(state.state_name, state.state_id)));
}

async function showDistricts(state_id) {
     const response = await fetch("https://cdn-api.co-vin.in/api/v2/admin/location/districts/"+state_id);
     const result = await response.json();
     var i, L = districtSelect.options.length - 1;
     for(i = L; i >= 0; i--) {
       districtSelect.remove(i);
     }
     districtSelect.add(new Option("Select a district", ""));
     result.districts.forEach(district => districtSelect.add(new Option(district.district_name, district.district_id)));
}

async function checkSubscription() {
  const registration = await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.getSubscription();
  if (subscription) {
    const response = await fetch("/api/isSubscribed", {
      method: 'POST',
      body: JSON.stringify({endpoint: subscription.endpoint}),
      headers: {
        "content-type": "application/json"
      }
    });
    const subscribed = await response.json();
    if (subscribed) {
      subscribeButton.disabled = true;
      unsubscribeButton.disabled = false;
    }
    return subscribed;
  }

  return false;
}

async function init() {
  fetch('/api/publicSigningKey')
     .then(response => response.arrayBuffer())
     .then(key => this.publicSigningKey = key)
     .finally(() => console.info('Application Server Public Key fetched from the server'));

  await navigator.serviceWorker.register("/serviceWorker.js");
  await navigator.serviceWorker.ready;
  console.info('Service Worker has been installed and is ready');
  navigator.serviceWorker.addEventListener('message', event => displayPreviousAlerts());
  displayPreviousSubscriptions();
  displayPreviousAlerts();
}

function displayPreviousSubscriptions() {
  caches.open('data').then(dataCache => {
    dataCache.match('districts')
      .then(response => response ? response.text() : '')
      .then(txt => txt ? previousSubscriptionsDiv.innerText = 'You are subscribed to ' + txt : previousSubscriptionsDiv.innerText = '');
  });
}

async function displayPreviousAlerts() {
  console.log('displayLastMessages');
  const dataCache = await caches.open('data');
  const alerts = await dataCache.match('alerts');
  if (dataCache && alerts) {
      const districts = await dataCache.match('districts');
      const alertsValue = await alerts.text();
      const districtsValue = await districts.text();
      previousAlertsDiv.innerHTML = alertsValue + '<br/> Please visit <a href="https://www.cowin.gov.in/home">cowin portal</a> for booking. <br/> Please note that you are automatically unsubscribed from ' + districtsValue + '. But you can re-subscribe if required';
  }
  previousSubscriptionsDiv.innerText = '';
}

async function unsubscribe() {
  const registration = await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.getSubscription();
  if (subscription) {
    const successful = await subscription.unsubscribe();
    if (successful) {
      console.info('Unsubscription successful');
      await fetch("/api/unsubscribe", {
        method: 'POST',
        body: JSON.stringify({endpoint: subscription.endpoint}),
        headers: {
          "content-type": "application/json"
        }
      });
      console.info('Unsubscription info sent to the server');
      subscribeButton.disabled = false;
      unsubscribeButton.disabled = true;
    } else {
      console.error('Unsubscription failed');
    }
  }
  if(window.location.hash === '#alert') {
    window.location.href = window.location.href.split('#')[0];
  }
}

async function subscribe() {
  console.log("subscribe ", districtSelect.value);
  if(!districtSelect.value)
    return;
  const registration = await navigator.serviceWorker.ready;
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: this.publicSigningKey
  });

  console.info(`Subscribed to Push Service: ${subscription.endpoint}`);

  let userSubscription = {};
  userSubscription.district = districtSelect.value;
  userSubscription.subscription = subscription;

  await fetch("/api/subscribe", {
    method: 'POST',
    body: JSON.stringify(userSubscription),
    headers: {
      "content-type": "application/json"
    }
  });
  console.info('Subscription info sent to the server');
  subscribeButton.disabled = true;
  unsubscribeButton.disabled = false;
  previousSubscriptionsDiv.innerText = 'You are subscribed to ' + districtSelect.value;
  previousAlertsDiv.innerText = "";

  const dataCache = await caches.open('data');
  await dataCache.put('districts', new Response(districtSelect.value));
  await dataCache.delete('alerts');
}