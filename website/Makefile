.PHONY: all install dev dev-test test test-browser release clean

all: install release

install:
	npm install

dev:
	npx shadow-cljs watch frontend test test-browser

dev-test:
	npx shadow-cljs watch frontend test-browser

test:
	npx shadow-cljs compile test

test-browser:
	npx shadow-cljs watch test-browser

release:
	npx shadow-cljs release frontend

clean:
	rm -rf out .shadow-cljs public/js public/index.html src/main/com/hjsoft/roadside/website/version.cljs
