#!/usr/bin/env bash

if [ ! -d "build" ]
then
    mkdir "build"
fi

javac ca/mcgill/ecse420/a1/MatrixMultiplication.java

cd build

java ca.mcgill.ecse420.a1.MatrixMultiplication