INCLUDES=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
CC = gcc $(INCLUDES) -Wall -fpic

OBJ = jocket_futex_Futex.o

all: jocket_futex_Futex.h lib

jocket_futex_Futex.h:
	javah -classpath ../../../classes jocket.futex.Futex

lib: $(OBJ)
	gcc -shared -o libJocketFutex.so $(OBJ) -lc

clean:
	rm -f *.o *.so *~ jocket_futex_Futex.h
