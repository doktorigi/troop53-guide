"""Find intersections of authored cubic centerlines for precise crossing masks."""
def cubic(points,t):
    a,b,c,d=points
    return tuple((1-t)**3*a[i]+3*(1-t)**2*t*b[i]+3*(1-t)*t*t*c[i]+t**3*d[i] for i in [0,1])
def cross(v,w): return v[0]*w[1]-v[1]*w[0]
def intersect(a,b,c,d):
    v=(b[0]-a[0],b[1]-a[1]); w=(d[0]-c[0],d[1]-c[1]); q=(c[0]-a[0],c[1]-a[1]); div=cross(v,w)
    if abs(div)<1e-9:return None
    u=cross(q,w)/div; t=cross(q,v)/div
    if 0<=u<=1 and 0<=t<=1:return (a[0]+u*v[0],a[1]+u*v[1])
curves={
 'ring-lower':[(330,190),(330,235),(265,255),(250,219)],
 'ring-upper':[(250,219),(227,165),(310,147),(347,208)],
 'standing':[(330,100),(330,130),(330,160),(330,190)],
 'ascending-low':[(278,289),(278,260),(284,223),(292,197)],
 'ascending-high':[(292,197),(299,171),(310,154),(318,143)],
 'return-high':[(335,164),(318,166),(317,183),(318,211)],
 'return-low':[(318,211),(319,237),(332,257),(341,280)],
}
for name,curve in curves.items():
    if name.startswith('ring'):continue
    for ring in ['ring-lower','ring-upper']:
        first=[cubic(curve,i/100) for i in range(101)]; second=[cubic(curves[ring],i/100) for i in range(101)]
        for i in range(100):
            for j in range(100):
                point=intersect(first[i],first[i+1],second[j],second[j+1])
                if point:print(name,ring,tuple(round(p,2) for p in point),'parameters',i/100,j/100)
print('Figure eight')
curves={
 'lower':[(348,235),(402,325),(469,307),(468,258)],
 'upper':[(468,258),(468,213),(423,189),(378,206)],
 'working':[(382,190),(394,230),(399,272),(377,295)],
 'tail':[(377,295),(368,304),(360,314),(351,322)]}
for ring in ['lower','upper']:
    for name in ['working','tail']:
        first=[cubic(curves[name],i/100) for i in range(101)]; second=[cubic(curves[ring],i/100) for i in range(101)]
        for i in range(100):
            for j in range(100):
                point=intersect(first[i],first[i+1],second[j],second[j+1])
                if point:print(name,ring,tuple(round(p,2) for p in point),'parameters',i/100,j/100)
