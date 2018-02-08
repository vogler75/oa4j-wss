# Websocket Server for WinCC OA

Connect programs (e.g. Python Program) to WinCC OA through a Websocket Manager. Programs can connect to WinCC OA and read/write/connect datapoints. Communication is JSON based, it’s simple to use for example with Python, see examples below.

dpGet
dpSet
dpConnect
dpQueryConnect
dpGetPeriod
… more functions will be implemented
Required Python modules:

pip3 install websocket-client
pip3 install matplotlib

# Setup
oa4j is needed:
copy WCCOAjava.exe to <project>\bin directory
copy WCCOAjava.dll to <project>\bin directory
  
copy wss\wss.jar to <project>\bin directory
copy wss\keystore.jks to <project>
WCCOAjava -num 1 -cp bin/wss.jar -c wss/Server
  
# Python Examples
############################################################
# Open Connection
############################################################
import json
import ssl
from websocket import create_connection
url='wss://rocworks.no-ip.org:80/winccoa?username=demo&password=demo'
ws = create_connection(url, sslopt={"cert_reqs": ssl.CERT_NONE})

############################################################
# dpGetPeriod
############################################################
cmd={'DpGetPeriod': {
 'Dps':['ExampleDP_Trend1.'],
 'T1': '2018-02-07T18:10:00.000', 
 'T2': '2018-02-07T23:59:59.999',
 'Count': 0, # Optional (Default=0)
 'Ts': 0 # Optional (0...no ts in result, 1...ts as ms since epoch, 2...ts as ISO8601)
 }}
ws.send(json.dumps(cmd))
res=json.loads(ws.recv())
#print(res)
if "System1:ExampleDP_Trend1.:_offline.._value" in res["DpGetPeriodResult"]["Values"]:
 values=res["DpGetPeriodResult"]["Values"]["System1:ExampleDP_Trend1.:_offline.._value"]
 print(values)
else:
 print("no data found")

# Plot result of dpGetPeriod
%matplotlib inline 
import matplotlib.pyplot as plt
plt.plot(values)
plt.ylabel('ExampleDP_Trend1.')
plt.show()

############################################################
# dpGet
############################################################
cmd={'DpGet': {'Dps':['ExampleDP_Trend1.', 'ExampleDP_Trend2.']}}
ws.send(json.dumps(cmd))
res=json.loads(ws.recv())
print(json.dumps(res, indent=4, sort_keys=True))

############################################################
# dpSet
############################################################
from random import randint
cmd={'DpSet': {'Wait': True, 
 'Values':[{'Dp':'ExampleDP_Trend1.','Value': randint(0, 9)}, 
 {'Dp':'ExampleDP_Trend2.','Value': randint(0, 9)}]}}
ws.send(json.dumps(cmd))
res=json.loads(ws.recv())
print(json.dumps(res, indent=4, sort_keys=True))

############################################################
# dpConnect
############################################################
from threading import Thread

def read():
    while True:
        res=json.loads(ws.recv())
        print(res)
Thread(target=read).start()
    
cmd={"DpConnect": {"Id": 1, "Dps": ["ExampleDP_Trend1."]}}
ws.send(json.dumps(cmd))
