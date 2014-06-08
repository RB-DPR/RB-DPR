# RB-DPR: Rochester-BIT implementation of the Deterministic Parallel Ruby

Deterministic Parallel Ruby (DPR) is a parallel dialect of the Ruby language.
RB-DPR implements DPR, together with a runtime determinism checker TARDIS.
RB-DPR is built on top of JRuby 1.7.11 source code, with DPR constructs added inside
the language virtual machine. 

Authors: 
  Li Lu (University of Rochester) <llu@cs.rochester.edu>
  Weixing Ji (Beijing Institute of Technology) <bitjwx@gmail.com>
  Michael L. Scott (University of Rochester) <scott@cs.rochester.edu>

## More information for running and building JRuby programs

JRuby (http://jruby.org/)

## More Information for Research

DPR and TARDIS (PLDI '14)

[Dynamic enforcement of determinism in aparallel scripting
language](http://dl.acm.org/citation.cfm?id=2594300), by Lu, Li, Weixing Ji,
and Michael L. Scott. . In Proceedings of the 35th ACM SIGPLAN Conference on
Programming Language Design and Implementation (PLDI), Edinburgh, Scotland,
June 2014. 

A previous workshop paper about the brief idea of TARDIS (WoDet '13)

[TARDIS: Task-level Access Race Detection by Intersecting
Sets](http://www.cs.rochester.edu/u/scott/papers/2013_WoDet_TARDIS.pdf), by
Weixing
Ji, Li  Lu, and Michael L. Scott.  4th Workshop on Determinism and Correctness in
Parallel Programming (WoDet), Houston, TX, Mar. 2013 (in conjunction with
ASPLOS XVIII). 

Formal semantic framework of deterministic parallel programming

[Toward a Formal Semantic Framework for Deterministic Parallel
Programming](https://cs.rochester.edu/u/scott/papers/2011_disc.pdf), by Li Lu
and Michael L. Scott.  25th International Symposium on Distributed Computing
(DISC), Rome, Italy, Sep. 2011.

