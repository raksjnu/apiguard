import urllib.request
import urllib.parse
import json
import time

BASE_URL = "http://localhost:4567"

def log(msg):
    print(f"[TEST] {msg}")

def post(endpoint, data=None):
    url = f"{BASE_URL}{endpoint}"
    try:
        req = urllib.request.Request(url, method="POST")
        if data:
            req.add_header('Content-Type', 'application/json')
            jsondata = json.dumps(data).encode('utf-8')
            req.data = jsondata
        
        with urllib.request.urlopen(req) as resp:
            raw_resp = resp.read().decode()
            try:
                data = json.loads(raw_resp)
                if isinstance(data, dict) and 'error' in data:
                    log(f"POST {endpoint}: API ERROR: {data['error']}")
                    return None
            except json.JSONDecodeError as e:
                log(f"POST {endpoint}: JSON PARSE ERROR: {e}")
                log(f"RAW RESPONSE: {raw_resp}")
                return None
            
            log(f"POST {endpoint}: SUCCESS ({resp.status})")
            return data
    except urllib.error.HTTPError as e:
        log(f"POST {endpoint}: FAILED ({e.code})")
        print(e.read().decode())
        return None
    except Exception as e:
        log(f"POST {endpoint}: ERROR {e}")
        return None

def get(endpoint):
    url = f"{BASE_URL}{endpoint}"
    try:
        with urllib.request.urlopen(url) as resp:
            log(f"GET {endpoint}: SUCCESS ({resp.status})")
            return json.loads(resp.read().decode())
    except Exception as e:
        log(f"GET {endpoint}: ERROR {e}")
        return None

def run_test():
    # 1. Start Broker
    log("Starting Embedded Broker...")
    post("/api/jms/embed/start")
    time.sleep(2)

    # 2. Connect
    log("Connecting to Broker...")
    config = {
        "provider": "ActiveMQ",
        "url": "tcp://localhost:61616",
        "username": "",
        "password": "",
        "factoryClass": "",
        "driverJarPath": ""
    }
    post("/api/jms/connect", config)

    # 3. Start Listener
    log("Starting Listener (1 consumer)...")
    listen_config = {
        "destination": "TEST.Q",
        "destType": "QUEUE",
        "consumers": "1"
    }
    post("/api/jms/listen/start", listen_config)

    # 4. Send Message
    log("Sending Message...")
    send_payload = {
        "destination": "TEST.Q",
        "destType": "QUEUE",
        "payload": "{\"msg\": \"Hello from Python Verifier\"}",
        "properties": {"testKey": "testVal"}
    }
    post("/api/jms/send", send_payload)

    time.sleep(2)

    # 5. Get Stats
    log("Getting Stats...")
    stats = get("/api/jms/listen/stats")
    if stats:
        print(json.dumps(stats, indent=2))
        if stats.get('totalConsumed', 0) > 0:
            log("Wait! Total Consumed > 0. SUCCESS!")
        else:
            log("WARNING: Total Consumed is 0. Check listener logic.")

    # 6. Stop Listener
    log("Stopping Listener...")
    post("/api/jms/listen/stop")
    log("Done.")

if __name__ == "__main__":
    run_test()
