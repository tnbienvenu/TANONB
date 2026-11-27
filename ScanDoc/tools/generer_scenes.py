# Usage : python3 tools/generer_scenes.py <dossier_sortie> <image_document> [<image_document> ...]
# Puis : SCANDOC_SCENES=<dossier_sortie> ./gradlew :app:testDebugUnitTest --tests "*DetectionBench*" --rerun -i
# Générateur de scènes de test : document en perspective sur fonds variés (+ scènes sans document).
from PIL import Image, ImageDraw, ImageFilter
import numpy as np, random, io, sys, os
OUT=sys.argv[1]; random.seed(7); np.random.seed(7)
docs=[Image.open(p).convert('RGB') for p in sys.argv[2:]]
W,H=768,1024
def coeffs(src,dst):
    m=[]
    for p1,p2 in zip(dst,src):
        m.append([p1[0],p1[1],1,0,0,0,-p2[0]*p1[0],-p2[0]*p1[1]]); m.append([0,0,0,p1[0],p1[1],1,-p2[1]*p1[0],-p2[1]*p1[1]])
    return np.linalg.solve(np.array(m,float),np.array(src,float).reshape(8))
def noise_img(scale, seed):
    rs=np.random.RandomState(seed); s=rs.rand(H//scale+2,W//scale+2)
    return np.asarray(Image.fromarray((s*255).astype('uint8')).resize((W,H),Image.BICUBIC)).astype(float)/255
def bg_wood(k):
    n=noise_img(6,k); base=np.array([200,178,140.])
    stripes=np.sin(np.linspace(0,60,W)[None,:]+n*6)*12
    return np.clip(base[None,None,:]+stripes[...,None]+(n[...,None]-.5)*20,0,255)
def bg_floral(k):
    rs=np.random.RandomState(k); img=Image.new('RGB',(W,H),(40,170,190)); d=ImageDraw.Draw(img)
    for i in range(260):
        x,y=rs.randint(0,W),rs.randint(0,H); r=rs.randint(10,40); c=[(30,90,200),(240,240,245),(230,120,200),(20,60,160)][rs.randint(0,4)]
        for a in range(6):
            ang=a*np.pi/3; d.ellipse((x+np.cos(ang)*r*.7-r*.35,y+np.sin(ang)*r*.7-r*.35,x+np.cos(ang)*r*.7+r*.35,y+np.sin(ang)*r*.7+r*.35),fill=c)
    # zones roses (drap)
    for i in range(3):
        x,y=rs.randint(0,W),rs.randint(0,H); d.ellipse((x-200,y-120,x+200,y+120),fill=(225,40,120))
    return np.asarray(img.filter(ImageFilter.GaussianBlur(1.5))).astype(float)
def bg_white(k):
    n=noise_img(30,k); return np.clip(np.array([218,216,210.])[None,None,:]+(n[...,None]-.5)*14,0,255)
def bg_dark(k):
    n=noise_img(20,k); return np.clip(np.array([55,48,42.])[None,None,:]+(n[...,None]-.5)*20,0,255)
def bg_tiles(k):
    img=np.zeros((H,W,3))+np.array([178,175,195.]); 
    for x in range(0,W,170): img[:,x:x+4]=110
    for y in range(0,H,170): img[y:y+4,:]=110
    n=noise_img(4,k); return np.clip(img+(n[...,None]-.5)*18,0,255)
def bg_red(k):
    n=noise_img(40,k); return np.clip(np.array([200,40,70.])[None,None,:]+(n[...,None]-.5)*60,0,255)
BGS={'bois':bg_wood,'fleurs':bg_floral,'blanc':bg_white,'sombre':bg_dark,'carrelage':bg_tiles,'rouge':bg_red}
def scene(name,bgname,k,with_doc=True,glare=False,shadow=False,laptop=False,gradient=True):
    rs=np.random.RandomState(k)
    img=BGS[bgname](k)
    if laptop:
        img[0:int(H*.18), int(W*.05):int(W*.8)]=np.array([175,178,185.]); img[int(H*.18):int(H*.19), int(W*.05):int(W*.8)]=60
    q=None
    if with_doc:
        doc=docs[k%len(docs)]
        cx,cy=W*(.5+rs.uniform(-.06,.06)),H*(.5+rs.uniform(-.06,.06))
        s=rs.uniform(.55,.8); ang=rs.uniform(-.35,.35)
        dw,dh=W*s*.8, W*s*.8*1.41
        if dh>H*.85: f=H*.85/dh; dw*=f; dh*=f
        pts=[(-dw/2,-dh/2),(dw/2,-dh/2),(dw/2,dh/2),(-dw/2,dh/2)]
        ca,sa=np.cos(ang),np.sin(ang)
        q=[(cx+x*ca-y*sa, cy+x*sa+y*ca) for x,y in pts]
        # perspective : rapprocher les coins du haut
        t=rs.uniform(0,.12); q[0]=(q[0][0]+(q[1][0]-q[0][0])*t, q[0][1]); q[1]=(q[1][0]-(q[1][0]-q[0][0])*t, q[1][1])
        dsz=doc.size
        c=coeffs([(0,0),(dsz[0],0),(dsz[0],dsz[1]),(0,dsz[1])],q)
        warped=np.asarray(doc.transform((W,H),Image.PERSPECTIVE,c,Image.BICUBIC)).astype(float)
        m=Image.new('L',(W,H),0); ImageDraw.Draw(m).polygon(q,fill=255); m=np.asarray(m.filter(ImageFilter.GaussianBlur(0.8))).astype(float)[...,None]/255
        # ombre portée sous la feuille
        sh=Image.new('L',(W,H),0); ImageDraw.Draw(sh).polygon([(x+6,y+8) for x,y in q],fill=90); sh=np.asarray(sh.filter(ImageFilter.GaussianBlur(8))).astype(float)[...,None]/255
        img=img*(1-sh*0.6)
        img=img*(1-m)+warped*0.93*m
    if gradient:
        gx=np.linspace(rs.uniform(.75,1),rs.uniform(.9,1.1),W)[None,:,None]; img=img*gx
    if shadow:
        sm=Image.new('L',(W,H),0); x0=rs.randint(0,W//2); ImageDraw.Draw(sm).polygon([(x0,0),(x0+250,0),(x0+100,H),(x0-150,H)],fill=110); sm=np.asarray(sm.filter(ImageFilter.GaussianBlur(40))).astype(float)[...,None]/255
        img=img*(1-sm*.55)
    if glare:
        gm=Image.new('L',(W,H),0); gx0=rs.randint(0,W//3); gy0=rs.randint(H//3,2*H//3); ImageDraw.Draw(gm).ellipse((gx0-160,gy0-110,gx0+160,gy0+110),fill=255); gm=np.asarray(gm.filter(ImageFilter.GaussianBlur(50))).astype(float)[...,None]/255
        img=img+(255-img)*gm*.65
    img=np.clip(img+np.random.RandomState(k+1).normal(0,4,img.shape),0,255).astype('uint8')
    # compression JPEG puis réduction 192x256 (comme l'application)
    b=io.BytesIO(); Image.fromarray(img).save(b,'JPEG',quality=80); im=Image.open(b).convert('RGB')
    im.save(os.path.join(OUT,name+'.jpg'),quality=90)
    sm=im.resize((192,256),Image.BILINEAR); open(os.path.join(OUT,name+'.rgb'),'wb').write(sm.tobytes())
    return name+' 192 256 '+(' '.join('%.4f %.4f'%(x/W,y/H) for x,y in q) if q else 'none')
lines=[]
k=0
for bg in BGS:
    for variant in range(4):
        k+=1
        lines.append(scene(f'{bg}{variant}',bg,k,glare=(variant==1),shadow=(variant==2),laptop=(variant==3)))
    k+=1
    lines.append(scene(f'{bg}_vide',bg,k,with_doc=False))
open(os.path.join(OUT,'manifest.txt'),'w').write('\n'.join(lines)+'\n'); print(len(lines),'scènes')
