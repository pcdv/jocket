INCLUDES=-I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux
CC = gcc -O2 $(INCLUDES) -Wall -fpic -g -ggdb
ARCH := $(shell getconf LONG_BIT)
OBJ = jocket_futex_Futex.o
LIB = libJocketFutex$(ARCH).so

all: jocket_futex_Futex.h lib

jocket_futex_Futex.h:
  # use classpath compatible with ant or gradle build
	javah -classpath ../../../classes:../../../build/classes/java/main jocket.futex.Futex

lib: $(OBJ)
	gcc -shared -o $(LIB) $(OBJ) -lc -lrt
	mkdir -p ../../../src/main/resources
	mkdir -p ../../../src/test/resources
	cp $(LIB) ../../../src/main/resources
	cp $(LIB) ../../../src/test/resources

clean:
	rm -f *.o *.so *~ jocket_futex_Futex.h
