import re
from datetime import datetime

with open("logs.txt", "r", encoding="utf8") as logs:
    lines = logs.readlines()[1:]


def getParams(string):
    match = re.search(r"N=([0-9]+) and M=([0-9]+)", string)
    return int(match[1]), int(match[2])


def getTime(string):
    match = re.search(r"[0-9]{4}-.*,[0-9]{3}", string)
    return datetime.strptime(match[0], "%Y-%m-%d %H:%M:%S,%f")


def getActor(string):
    match = re.search(r" p([0-9]+) ", string)
    return int(match[1])


def getOperation(string):
    match = re.search(r"put|get|got", string)
    return match[0].replace("got", "get")


def isStarting(string):
    match = re.search("is launching", string)
    return match is not None


def getValues(string):
    match = re.search(r"value \[([0-9]+)\] with timestamp \[([0-9]+)\]", string)
    return int(match[1]), int(match[2])


def getChrono(string):
    match = re.search(r"([0-9]+)μs", string)
    return int(match[1])
