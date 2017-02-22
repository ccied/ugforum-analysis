import sys

price = sys.argv[1]
train = sys.argv[2]
test = sys.argv[3]
dev = sys.argv[4]

pt = {}
optrain = open('price-train-annotations.txt', 'a')
optest = open('price-test-annotations.txt', 'a')
opdev = open('price-dev-annotations.txt', 'a')


with open(price) as pf:
     for aline in pf:
         post = aline.split()[0]
         if post not in pt:
            pt[post] = ""
         pt[post]+= aline

with open(train) as tr:
     for aline in tr:
         post = aline.split()[0]
         if post in pt:
            optrain.write(pt[post])

with open(test) as tr:
     for aline in tr:
         post = aline.split()[0]
         if post in pt:
            optest.write(pt[post])

with open(dev) as tr:
     for aline in tr:
         post = aline.split()[0]
         if post in pt:
           opdev.write(pt[post])
      	
