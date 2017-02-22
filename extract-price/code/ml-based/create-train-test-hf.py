import sys

price = sys.argv[1]

pt = {}
optrain = open('hf-price-train-annotations.txt', 'a')
optest = open('hf-price-test-annotations.txt', 'a')
opdev = open('hf-price-dev-annotations.txt', 'a')


with open(price) as pf:
     for aline in pf:
         post = aline.split()[0]
         if post not in pt:
            pt[post] = ""
         pt[post]+= aline

total = len(pt)
test = 100
trainsize = total - 2*test


sortedpost = sorted(pt)

i = -1
for apost in sortedpost:
     i+= 1
     if i < trainsize:
       optrain.write(pt[apost])

     elif i >= trainsize and i < trainsize+ test:
       opdev.write(pt[apost])

     elif i >= trainsize+ test:
       optest.write(pt[apost])
