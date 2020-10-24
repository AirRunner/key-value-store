import re
from datetime import datetime

with open("logs.txt", "r", encoding="utf8") as logs:
    lines = logs.readlines()[1:]


def getParam(string):
    res = re.findall(r"[0-9]+", re.search(r"N=[0-9]* and M=[0-9]*", string).group())
    return int(res[0]), int(res[1])


def getTime(string):
    res = re.search(r"[0-9]{4}-.*,[0-9]{3}", string).group()
    return datetime.strptime(res, "%Y-%m-%d %H:%M:%S,%f")


def getActor(string):
    res = re.search(r"[0-9]+", re.search(r"\] - p[0-9]+", string).group()).group()
    return int(res)


def getOperation(string):
    res = re.search(r"put|get|got", string).group()
    return res.replace("o", "e")


def isStarting(string):
    res = re.search("is launching", string)
    return res is not None


def getValues(string):
    res = re.search(r"value \[[0-9]+\] with timestamp \[[0-9]+\]", string).group()
    res = re.findall(r"[0-9]+", res)
    return int(res[0]), int(res[1])


def getChrono(string):
    res = re.search(r"[0-9]+μs", string).group()
    res = re.search(r"[0-9]+", res).group()
    return int(res)
