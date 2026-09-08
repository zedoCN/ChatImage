#!/usr/bin/env python3
"""Exercise the upload protocol against an owned test client and loopback Fabric server.

Requires a connected MCPFabric client. Config credentials stay in memory.
Temporarily wraps the test client's receiver to record responses, preserving normal handling.
"""
import argparse, base64, json, time, uuid, urllib.request
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--bridge-config', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
config = json.loads(args.bridge_config.read_text())
assert config['host'] in ('127.0.0.1', 'localhost')

def rpc(body):
    request = urllib.request.Request(f"http://127.0.0.1:{config['port']}/rpc", data=json.dumps({'method':'unsafe.javaScratch','params':{'body':body,'mainThread':True}}).encode(), headers={'Content-Type':'application/json','Authorization':'Bearer '+config['token']})
    result = json.load(urllib.request.urlopen(request, timeout=30))
    assert result['ok'], result
    return result['result']['result']

rpc('var api=Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");\nvar type=Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type");\nvar handler=Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking$PlayPayloadHandler");\napi.getMethod("unregisterReceiver",net.minecraft.resources.Identifier.class).invoke(null,io.github.kituin.chatimage.transfer.TransferPayload.ID.id());\nObject proxy=java.lang.reflect.Proxy.newProxyInstance(handler.getClassLoader(),new Class[]{handler},(o,m,a)->{\n    if(m.getName().equals("receive")) {\n        var payload=(io.github.kituin.chatimage.transfer.TransferPayload)a[0];\n        System.setProperty("chatimage.test.reply",payload.json());\n        var receiver=io.github.kituin.chatimage.transfer.ClientTransfers.class.getDeclaredMethod("receive",String.class);receiver.setAccessible(true);receiver.invoke(null,payload.json());\n    }\n    return null;\n});\napi.getMethod("registerReceiver",type,handler).invoke(null,io.github.kituin.chatimage.transfer.TransferPayload.ID,proxy);return "Receiver installed";')

def send(op, request_id=None, **values):
    request_id = request_id or str(uuid.uuid4())
    packet = json.dumps(dict(op=op,id=request_id,**values))
    rpc('System.clearProperty("chatimage.test.reply"); Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking").getMethod("send",net.minecraft.network.protocol.common.custom.CustomPacketPayload.class).invoke(null,new io.github.kituin.chatimage.transfer.TransferPayload('+json.dumps(packet)+')); return "sent";')
    for _ in range(60):
        value = rpc('return System.getProperty("chatimage.test.reply", "");')
        if value:
            result = json.loads(value)
            if result['id'] == request_id:return result
        time.sleep(0.1)
    raise AssertionError('No protocol response')

results = []
def expect(result, **fields):
    for key,value in fields.items(): assert result.get(key)==value,result
    results.append(result)

caps = send('hello');expect(caps,op='caps',enabled='true')
expect(send('begin',size=int(caps['max'])+1),op='error',reason='size')
expect(send('get',server=str(uuid.uuid4()),hash='0'*64),op='error',reason='server')
expect(send('get',server=caps['server'],hash='../server.properties'),op='error',reason='reference')
expect(send('get',server=caps['server'],hash='0'*64),op='error',reason='missing')
request_id=str(uuid.uuid4());expect(send('begin',request_id,size=8),op='ready')
expect(send('chunk',request_id,offset=1,data=base64.b64encode(b'abcdefgh').decode()),op='error',reason='sequence')
expect(send('begin',size=8),op='error',reason='rate')
time.sleep(5.1)
request_id=str(uuid.uuid4());expect(send('begin',request_id,size=8),op='ready')
expect(send('chunk',request_id,offset=0,data=base64.b64encode(b'abcdefgh').decode()),op='error',reason='format')
args.output.write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n')
print(f'PASS {len(results)} real network protocol assertions')
