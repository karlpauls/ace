# Licensed to the Apache Software Foundation (ASF) under the terms of ASLv2 (http://www.apache.org/licenses/LICENSE-2.0).

def libDir = new File("lib")
def output = new File('libs.txt')
output.text = ""

libDir.listFiles().each {
	output << "@lib/${it.name},\\"
	output << '\n'
}

