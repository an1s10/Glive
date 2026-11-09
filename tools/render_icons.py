from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math, sys
S=4
RED=(229,24,43); ORANGE=(255,104,56)

def grad(w,h,c1,c2,angle=45):
    g=Image.new("RGB",(w,h))
    px=g.load()
    a=math.radians(angle); dx,dy=math.cos(a),math.sin(a)
    m=abs(dx)*w+abs(dy)*h
    for y in range(h):
        for x in range(w):
            t=((x*dx+y*dy)-min(0,dx*w)-min(0,dy*h))/m
            t=max(0,min(1,t))
            px[x,y]=tuple(int(c1[i]+(c2[i]-c1[i])*t) for i in range(3))
    return g

def background(w,h,cx,cy,r):
    bg=Image.new("RGB",(w,h),(10,12,16))
    glow=Image.new("L",(w,h),0)
    d=ImageDraw.Draw(glow); d.ellipse([cx-r,cy-r,cx+r,cy+r],fill=110)
    glow=glow.filter(ImageFilter.GaussianBlur(r*0.6))
    tint=Image.new("RGB",(w,h),(70,14,20))
    return Image.composite(tint,bg,glow)

def mark(img,cx,cy,R,stroke):
    w,h=img.size
    mask=Image.new("L",(w,h),0); d=ImageDraw.Draw(mask)
    box=[cx-R,cy-R,cx+R,cy+R]
    d.arc(box,start=0,end=318,fill=255,width=stroke)
    a=math.radians(318); rr=R-stroke/2
    px,py=cx+rr*math.cos(a),cy+rr*math.sin(a)
    d.ellipse([px-stroke/2,py-stroke/2,px+stroke/2,py+stroke/2],fill=255)
    # barre du G, raccordée au bord extérieur de l'anneau
    d.rectangle([cx+R*0.30,cy-stroke/2,cx+R-1,cy+stroke/2],fill=255)
    d.ellipse([cx+R*0.30-stroke/2,cy-stroke/2,cx+R*0.30+stroke/2,cy+stroke/2],fill=255)
    # on remplit l'angle sous la barre pour qu'elle se fonde dans l'anneau
    d.pieslice(box,start=0,end=12,fill=255)
    hole=Image.new("L",(w,h),0); dh=ImageDraw.Draw(hole)
    ri=R-stroke
    dh.pieslice([cx-ri,cy-ri,cx+ri,cy+ri],start=0,end=14,fill=255)
    dh.rectangle([cx-R,cy-R,cx+R,cy+stroke/2],fill=0)
    mask.paste(0,(0,0),hole)
    circ=Image.new("L",(w,h),0); ImageDraw.Draw(circ).ellipse(box,fill=255)
    from PIL import ImageChops
    mask=ImageChops.multiply(mask,circ)
    g=grad(w,h,ORANGE,RED,60)
    img.paste(g,(0,0),mask)
    tri=Image.new("L",(w,h),0); dt=ImageDraw.Draw(tri)
    th=R*0.62
    x0=cx-R*0.40
    dt.polygon([(x0,cy-th/2),(x0,cy+th/2),(x0+th*0.92,cy)],fill=255)
    img.paste((250,250,252),(0,0),tri)

def icon(size,path):
    W=size*S
    img=background(W,W,W/2,W/2,W*0.55)
    mark(img,W*0.5,W*0.5,W*0.33,int(W*0.11))
    img.resize((size,size),Image.LANCZOS).save(path)

def banner(w,h,path):
    W,H=w*S,h*S
    img=background(W,H,W*0.32,H*0.5,H*0.9)
    f=ImageFont.truetype("/usr/share/fonts/opentype/inter/InterDisplay-Black.otf",int(H*0.34))
    d=ImageDraw.Draw(img)
    bb=d.textbbox((0,0),"LIVE",font=f); cap=bb[3]-bb[1]
    R=cap*0.56
    tw=d.textlength("LIVE",font=f)
    total=2*R+H*0.03+tw
    x=(W-total)/2
    mark(img,x+R,H*0.5,R,int(R*0.36))
    tx=x+2*R+H*0.03
    d.text((tx,H*0.5-cap/2-bb[1]),"LIVE",font=f,fill=(244,245,247))
    img.resize((w,h),Image.LANCZOS).save(path)

if __name__=="__main__":
    out=sys.argv[1]
    import os
    for name,s in [("mdpi",48),("hdpi",72),("xhdpi",96),("xxhdpi",144),("xxxhdpi",192)]:
        os.makedirs(f"{out}/mipmap-{name}",exist_ok=True)
        icon(s,f"{out}/mipmap-{name}/ic_launcher.png")
    os.makedirs(f"{out}/drawable-xhdpi",exist_ok=True)
    banner(640,360,f"{out}/drawable-xhdpi/banner.png")
    icon(512,f"/home/claude/icon/preview_icon.png")
    banner(640,360,f"/home/claude/icon/preview_banner.png")
