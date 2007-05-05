#!/usr/bin/python
import base64, cgi, re

def output_results(result):
    print "Content-Type: text/xml\n\n"
    
    print "<?xml version=\"1.0\"?>\n"
    print "<base64>", result, "</base64>"

def main():
    form = cgi.FieldStorage()
    if form.has_key("v"):
        badvals = base64.decodestring(form["v"].value)
        valrepair = re.compile('-_-IQUITY-_-')
        goodvals = valrepair.sub("\x00", badvals)
        finalvals = base64.encodestring(goodvals).replace('\n', '')
        output_results(finalvals)
		
main()
