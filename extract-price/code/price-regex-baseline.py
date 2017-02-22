#Computes the precision/recall of a simple regex-based price extractor
#that extracts all the numbers and known currencies from a post

import os
import sys
import re


if len(sys.argv) < 2:
   print ('Missing arguments. Usgae:\n python {} <raw post directory> <merged price annotation>'.format(sys.argv[0]))
   sys.exit(0)

indir = sys.argv[1]
infile = sys.argv[2]
allprices = {}
allnumbers = {}
tp = 0
fp = 0
total = 0
currencies = 'gbp|cny|czk|eur|inr|ils|jpy|omr|krw|thb|usd|omc|wmz|lr|bloocoins|btc|bitcoin|coinye|dogecoin|libertyreserve|ltc|litecoin|moneygram|mp|moneypak|okpay|omnicoin|psc|paysafecard|pz|payza|pf|perfectmoney|perfect money|pp|paypal|skrill|ukash|dollars|united states dollar|wdc|world coins|wu|western union|webmoney|wmz|zetacoin|cc|credit|card|credit card|ego|egopay|moneybooker|booker|preev|alterpay|flappycoins|pound'

with open(infile) as ifile:
    for aline in ifile:
        #find the file in indir
        allparts = aline.split()
        nfile = allparts[0]
        if nfile not in allprices:
           allprices[nfile] = []
        thefile = open(indir + '/0-initiator' + nfile + '.txt.tok').read()
        allprices[nfile].append(thefile[int(allparts[2]) : int(allparts[3])].lower())

for afile in allprices:
        thefile = open(indir + '/0-initiator' + afile + '.txt.tok')

        #get all the numbers from the file
        wholefile = thefile.read()
        allnumbers = re.findall(r'\d+\.\d+|\d+|'+currencies, wholefile)

        if allnumbers:
           allnumbers = set(allnumbers)
        #check how many are prices
       
        setprice = set(allprices[afile])

        for anumber in setprice:
             total+= 1
             if anumber in allnumbers:
                 tp+= 1
        for anumber in allnumbers:
              if anumber not in setprice:
                  fp+= 1 


        print ('numbers->', allnumbers)
        print ('annotations->', setprice)

print('total->', total)

print ('tp->', tp, ' fp->', fp, ' precision->', tp*1.0/(tp+fp), 'recall->',tp*1.0/total )        
