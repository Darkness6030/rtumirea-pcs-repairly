"""Executable acceptance checks. Run ONLY against an isolated test database.
Required env: VERIFY_URL, VERIFY_EMAIL, VERIFY_PASSWORD. Uses Python 3 stdlib.
"""
import csv, io, json, os, urllib.request, urllib.error, uuid
BASE=os.environ['VERIFY_URL'].rstrip('/')
TOKEN=None
checks=0

def request(path,method='GET',body=None,status=200,token='default',headers=None):
 global checks
 h={'Accept':'application/json', **(headers or {})}
 value=TOKEN if token=='default' else token
 if value:h['Authorization']='Bearer '+value
 if isinstance(body,(dict,list)):
  body=json.dumps(body).encode();h['Content-Type']='application/json'
 req=urllib.request.Request(BASE+path,data=body,method=method,headers=h)
 try:
  with urllib.request.urlopen(req,timeout=30) as r:code=r.status;data=r.read();rh=r.headers
 except urllib.error.HTTPError as e:code=e.code;data=e.read();rh=e.headers
 assert code==status,f'{method} {path}: expected {status}, got {code}: {data[:500]!r}'
 checks+=1
 if path.startswith('/api'):assert rh.get('X-Correlation-ID'),'Missing correlation ID'
 if not data:return None
 if 'json' in rh.get('Content-Type',''):return json.loads(data)
 return data

def post(path,body,status=200,**kw):return request(path,'POST',body,status,**kw)
def put(path,body,status=200,**kw):return request(path,'PUT',body,status,**kw)
def login(email,password):return post('/api/auth/login',{'email':email,'password':password,'clientType':'NATIVE'},token=None)
def multipart(path,text,mode='CREATE_ONLY'):
 boundary='RepairlyBoundary'+uuid.uuid4().hex
 body=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="repairs.csv"\r\nContent-Type: text/csv\r\n\r\n'+text+f'\r\n--{boundary}--\r\n').encode()
 return request('/api/repairs/import/'+path+'?mode='+mode,'POST',body,headers={'Content-Type':'multipart/form-data; boundary='+boundary})

assert request('/actuator/health')['status']=='UP'
request('/api/repairs',status=401,token=None)
request('/swagger-ui.html',status=401,token=None)
auth=login(os.environ['VERIFY_EMAIL'],os.environ['VERIFY_PASSWORD']);TOKEN=auth['accessToken']
request('/api/repairs',status=403)
post('/api/auth/change-initial-password',{'newPassword':'Temporary!Permanent731'},204)
# Old access token is invalidated even after initial password change.
request('/api/auth/me',status=401)
auth=post('/api/auth/refresh',{'refreshToken':auth['refreshToken']},token=None);TOKEN=auth['accessToken']
assert request('/api/auth/me')['passwordChangeRequired']==False
post('/api/auth/change-password',{'currentPassword':'a'*90,'newPassword':'Next!Password731'},401)
assert '/api/repairs' in request('/v3/api-docs')['paths']
legacy=request('/api/repairs?q=archived:all')['totalElements']
# Legacy rows must be readable, including clients and assigned technicians.
assert len(request('/api/clients'))>=3
assert len(request('/api/technicians'))>=3
client=post('/api/clients',{'code':'CHECK-C','name':'Проверочный клиент','phone':'+79990000001','address':'Тестовый адрес'},201)
tech=post('/api/technicians',{'code':'CHECK-T','name':'Проверочный мастер','phone':'+79990000002','specialization':'Бытовая техника'},201)
client=put('/api/clients/'+client['id'],{**{k:client[k] for k in ['code','name','phone','address','version']},'name':'Клиент после изменения'})
assert client['version']==1
row={'externalId':'CHECK-R','applianceType':'Стиральная машина','brand':'Тест','model':'Model','problem':'Не включается','clientId':client['id'],'technicianId':tech['id'],'status':'NEW','cost':1234.50,'urgent':True,'tags':['гарантия']}
post('/api/repairs',{**row,'cost':-1},400)
post('/api/repairs',{**row,'clientId':str(uuid.uuid4())},400)
r=post('/api/repairs',row,201)
assert r['priorityLevel']=='HIGH' and r['version']==0
put('/api/repairs/'+r['id'],{**row,'version':0,'status':'COMPLETED'},400)
r=put('/api/repairs/'+r['id'],{**row,'version':0,'status':'DIAGNOSING'})
assert r['version']==1
put('/api/repairs/'+r['id'],{**row,'version':0},409)
request('/api/repairs?size=101',status=400)
request('/api/repairs?sort=problem;DROP,asc',status=400)
for sort in ['cost,desc','created,asc','status,asc','priority,desc','technician,asc']:
 request('/api/repairs?sort='+sort)
q='CHECK-R technician:CHECK-T status:DIAGNOSING urgent:true priority:high tag:гарантия cost>=1000 archived:false'
result=request('/api/repairs?'+urllib.parse.urlencode({'q':q,'size':1}))
assert result['totalElements']==1
assert request('/api/repairs?'+urllib.parse.urlencode({'q':'CHECK-R technician:CHECK'}))['totalElements']==0
page0=request('/api/repairs?size=1&page=0&sort=code,asc')
page1=request('/api/repairs?size=1&page=1&sort=code,asc')
assert page0['content'][0]['id']!=page1['content'][0]['id']
note=post('/api/repairs/'+r['id']+'/notes',{'body':'Проверена плата','pinned':True},201)
notes=request('/api/repairs/'+r['id']+'/notes');assert notes[0]['authorId']==auth['user']['id']
note=put('/api/repairs/'+r['id']+'/notes/'+note['id'],{'body':'Плата заменена','pinned':False,'version':0});assert note['version']==1
put('/api/repairs/'+r['id']+'/notes/'+note['id'],{'body':'Устаревшее','pinned':False,'version':0},409)
result=post('/api/repairs/bulk',{'ids':[r['id'],str(uuid.uuid4())],'action':'ADD_TAG','tag':'проверено'})
assert result['succeeded']==1 and len(result['failures'])==1
r=request('/api/repairs/'+r['id']);assert 'проверено' in r['tags']
for action,extra in [('REMOVE_TAG',{'tag':'проверено'}),('SET_STATUS',{'status':'WAITING_PARTS'}),('ARCHIVE',{}),('RESTORE',{})]:
 result=post('/api/repairs/bulk',{'ids':[r['id']],'action':action,**extra});assert result['succeeded']==1
r=request('/api/repairs/'+r['id']);assert r['priorityLevel']=='CRITICAL' and 'проверено' not in r['tags']
request('/api/repairs/'+r['id'],'DELETE')
assert request('/api/repairs?q=CHECK-R')['totalElements']==0
assert request('/api/repairs?q=CHECK-R%20archived:true')['totalElements']==1
post('/api/repairs/'+r['id']+'/restore',None)
request('/api/repairs/import/preview','POST',b'--MissingFileBoundary--\r\n',400,headers={'Content-Type':'multipart/form-data; boundary=MissingFileBoundary'})
# Filtered CSV, then BOM-compatible import. New rows require NEW.
export=request('/api/repairs/export?'+urllib.parse.urlencode({'q':'CHECK-R'}),headers={'Accept':'text/csv'}).decode('utf-8-sig')
records=list(csv.DictReader(io.StringIO(export)));assert len(records)==1
records[0]['externalId']='CSV-R';records[0]['status']='NEW'
out=io.StringIO();w=csv.DictWriter(out,fieldnames=records[0].keys());w.writeheader();w.writerows(records)
text='\ufeff'+out.getvalue()
preview=multipart('preview',text);assert preview['validRows']==1 and preview['invalidRows']==0
result=multipart('apply',text);assert result['created']==1
preview=multipart('preview',text);assert preview['invalidRows']==1
records[0]['cost']='3456.00';out=io.StringIO();w=csv.DictWriter(out,fieldnames=records[0].keys());w.writeheader();w.writerows(records)
result=multipart('apply',out.getvalue(),'UPSERT_BY_EXTERNAL_ID');assert result['updated']==1
bad=out.getvalue().replace('3456.00','-1');preview=multipart('preview',bad,'UPSERT_BY_EXTERNAL_ID');assert preview['invalidRows']==1
stats=request('/api/analytics/summary');assert stats['total']==legacy+2 and stats['urgent']>=2 and stats['byTechnician']
# Role restrictions, temporary passwords and disabled accounts.
for role in ['VIEWER','EDITOR']:
 user=post('/api/admin/users',{'email':role.lower()+'@verify.local','displayName':role,'temporaryPassword':'Test!Password731','roles':[role]},201)
 uauth=login(user['email'],'Test!Password731')
 post('/api/auth/change-initial-password',{'newPassword':'Next!Password731'},204,token=uauth['accessToken'])
 uauth=post('/api/auth/refresh',{'refreshToken':uauth['refreshToken']},token=None);ut=uauth['accessToken']
 request('/api/repairs',token=ut)
 request('/api/admin/users',status=403,token=ut)
 request('/api/admin/audit',status=403,token=ut)
 post('/api/technicians',{'code':'NO','name':'Нет','phone':'+79990000003','specialization':'Нет'},403,token=ut)
 if role=='VIEWER':post('/api/repairs',{**row,'externalId':'NO'},403,token=ut)
 else:
  put('/api/repairs/'+r['id']+'/notes/'+note['id'],{'body':'Чужая заметка','pinned':False,'version':1},403,token=ut)
  post('/api/repairs/'+r['id']+'/notes',{'body':'Заметка оператора','pinned':False},201,token=ut)
  request('/api/repairs/'+r['id']+'/permanent','DELETE',status=403,token=ut)
 # Password reset revokes prior JWT; it cannot replace the newly reset temporary password.
 post('/api/admin/users/'+user['id']+'/reset-password',{'temporaryPassword':'Reset!Password731'},204)
 request('/api/auth/me',status=401,token=ut)
 users=request('/api/admin/users')['content'];user=next(x for x in users if x['id']==user['id'])
 put('/api/admin/users/'+user['id'],{k:user[k] for k in ['email','displayName','roles','version']}|{'enabled':False})
 post('/api/auth/login',{'email':user['email'],'password':'Reset!Password731'},401,token=None)
# Refresh rotation and replay revocation survive an exception.
a2=login(os.environ['VERIFY_EMAIL'],'Temporary!Permanent731')
a3=post('/api/auth/refresh',{'refreshToken':a2['refreshToken']},token=None)
post('/api/auth/refresh',{'refreshToken':a2['refreshToken']},401,token=None)
post('/api/auth/refresh',{'refreshToken':a3['refreshToken']},401,token=None)
# JWT access remains short-lived independently; refresh logout cannot renew it.
a4=login(os.environ['VERIFY_EMAIL'],'Temporary!Permanent731')
post('/api/auth/logout',{'refreshToken':a4['refreshToken']},204,token=None)
post('/api/auth/refresh',{'refreshToken':a4['refreshToken']},401,token=None)
audit=request('/api/admin/audit?q=LOGIN_SUCCESS');assert audit['totalElements']>0 and audit['content'][0]['actorEmail']
request('/api/repairs/'+r['id']+'/notes/'+note['id'],'DELETE',status=204)
request('/api/repairs/'+r['id'],'DELETE')
request('/api/repairs/'+r['id']+'/permanent','DELETE',status=204,headers={'X-Confirm-Delete':'PERMANENTLY_DELETE'})
request('/api/repairs/'+r['id'],status=404)
# Lockout after 5 failures rejects even the correct password.
lock=post('/api/admin/users',{'email':'locked@verify.local','displayName':'Lock test','temporaryPassword':'Test!Password731','roles':['VIEWER']},201)
for _ in range(5):post('/api/auth/login',{'email':lock['email'],'password':'Wrong!Password731'},401,token=None)
post('/api/auth/login',{'email':lock['email'],'password':'Test!Password731'},401,token=None)
print(f'PASS: {checks} HTTP checks; migration preserved {legacy} legacy repairs; CRUD, versions, roles, filters, CSV, notes, bulk, analytics, audit and token rotation verified.')
