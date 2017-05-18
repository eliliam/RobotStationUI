import socket, sys, time
from random import randint

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
try:
    s.bind(('localhost',6767));
except socket.error as e:
    print "Bind failed. "+e[1]
    sys.exit()

s.listen(10)
print "Listening"

while 1:

    conn, addr = s.accept()
    print "Connected to "+addr[0]
    try:
        while 1:
            toDo = randint(3,10)
            toSendArr = []
            for i in range(int(toDo)):
                base = randint(10,300)
                toSend = {
                        'x1': base,
                        'y1': base,
                        'x2': base+30,
                        'y2': base+ 70
                        }
                toSendArr.append(toSend)
            toSend = str(toSendArr)+"\n"
            print toSend
            conn.sendall(toSend)
            time.sleep(3)
    finally:
        conn.close()

s.close()
