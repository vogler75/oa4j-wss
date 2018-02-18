import json
import ssl
import time
from websocket import create_connection
from threading import Thread
from random import randint

url='wss://rocworks.no-ip.org/winccoa?username=demo&password=demo'
#ws = create_connection(url, sslopt={"cert_reqs": ssl.CERT_NONE})
ws = create_connection(url)


def read():
    while True:
        res=json.loads(ws.recv())
        print(json.dumps(res, indent=4, sort_keys=True))


Thread(target=read).start()

# DpGet
cmd={'DpGet': {'Id': 1, 'Dps':['ExampleDP_Trend1.', 'ExampleDP_Trend2.']}}
ws.send(json.dumps(cmd))
'''
{
    "DpGetResult": {
        "Error": 0,
        "Values": {
            "System1:ExampleDP_Trend1.:_original.._value": 6.0,
            "System1:ExampleDP_Trend2.:_original.._value": 6.0
        }
    }
}
'''

# DpSet
cmd={'DpSet': {'Id': 2, 'Wait': True,
 'Values':[{'Dp':'ExampleDP_Trend1.','Value': randint(0, 9)},
 {'Dp':'ExampleDP_Trend2.','Value': randint(0, 9)}]}}
ws.send(json.dumps(cmd))

# DpConnect
cmd={"DpConnect": {"Id": 3, "Dps": ["ExampleDP_Trend1."]}}
ws.send(json.dumps(cmd))
'''
{
    "DpConnectResult": {
        "Error": 0,
        "Id": 1,
        "Values": {
            "System1:ExampleDP_Trend1.:_online.._value": 6.0
        }
    }
}
'''


# DpQueryConnect
cmd={'DpQueryConnect': {'Id': 4, 'Query':"SELECT '_online.._value' FROM 'ExampleDP_Arg*.'", 'Answer': True}}
ws.send(json.dumps(cmd))
'''
{
    "DpQueryConnectResult": {
        "Error": 0,
        "Header": [
            "",
            ":_online.._value"
        ],
        "Values": [
            [
                "System1:ExampleDP_Trend1.",
                6.0
            ]
        ]
    }
}
'''

cmd={'DpGetPeriod': {
 'Id': 5,
 'Dps':['ExampleDP_Trend1.'],
 'T1': '2018-02-09T20:10:00.000',
 'T2': '2018-02-09T23:59:59.999',
 'Count': 0, # Optional (Default=0)
 'Ts': 0 # Optional (0...no ts in result, 1...ts as ms since epoch, 2...ts as ISO8601)
 }}
ws.send(json.dumps(cmd))

time.sleep(60)

print("end.")
ws.close()
