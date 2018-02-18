import json
import ssl
import matplotlib.pyplot as plt

from websocket import create_connection

url='wss://localhost:8443/winccoa?username=demo&password=demo'
ws = create_connection(url, sslopt={"cert_reqs": ssl.CERT_NONE})

cmd={'DpGetPeriod': {
 'Id': 5,
 'Dps':['ExampleDP_Trend1.'],
 'T1': '2018-02-09T20:00:00.000',
 'T2': '2018-02-09T23:59:59.999',
 'Count': 0, # Optional (Default=0)
 'Ts': 0 # Optional (0...no ts in result, 1...ts as ms since epoch, 2...ts as ISO8601)
 }}
ws.send(json.dumps(cmd))
res=json.loads(ws.recv())
print(res)
if "System1:ExampleDP_Trend1.:_offline.._value" in res["DpGetPeriodResult"]["Values"]:
    values=res["DpGetPeriodResult"]["Values"]["System1:ExampleDP_Trend1.:_offline.._value"]
    print(values)
    plt.plot(values)
    plt.ylabel('ExampleDP_Trend1.')
    plt.show()
else:
    print("no data found")

ws.close()