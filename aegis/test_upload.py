import requests

url = 'http://localhost:8000/api/validate'
files = {'archive': open('test.zip', 'rb')}
data = {'mode': 'zip'}

try:
    response = requests.post(url, files=files, data=data)
    print(f"Status Code: {response.status_code}")
    print(f"Response Body: {response.text}")
except Exception as e:
    print(f"Error: {e}")
