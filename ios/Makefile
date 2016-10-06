spyroad: main.swift libspyroad.a
	swiftc -import-objc-header libspyroad.h $< libspyroad.a -o $@ -L.

libspyroad.a: ../common/main.cpp
	clang -DLIBRARY -std=c++11 -c $<
	ar rcs $@ main.o

clean:
	-@rm spyroad
	-@rm main.o
	-@rm libspyroad.a


