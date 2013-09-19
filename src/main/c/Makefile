INCLUDES=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
CC = gcc -O2 $(INCLUDES) -Wall -fpic
ARCH := $(shell getconf LONG_BIT)
OBJ = jocket_futex_Futex.o
LIB = libJocketFutex$(ARCH).so

all: jocket_futex_Futex.h lib

jocket_futex_Futex.h:
	javah -classpath ../../../classes jocket.futex.Futex

lib: $(OBJ)
	gcc -shared -o $(LIB) $(OBJ) -lc -lrt
	cp $(LIB) ../../../classes

clean:
	rm -f *.o *.so *~ jocket_futex_Futex.h
