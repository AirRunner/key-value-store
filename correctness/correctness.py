import re
import os
from datetime import datetime


def parseParams(string):
    match = re.search(r"N=([0-9]+) and M=([0-9]+)", string)
    return int(match[1]), int(match[2])


def parseTime(string):
    match = re.search(r"[0-9]{4}-.*(\.|,)[0-9]{3,6}", string)
    return datetime.strptime(match[0], "%Y-%m-%d %H:%M:%S{}%f".format(match[1]))


def parseActor(string):
    match = re.search(r" p([0-9]+) ", string)
    return int(match[1])


def parseOperation(string):
    match = re.search(r"put|get|got", string)
    return match[0].replace("got", "get")


def isStarting(string):
    match = re.search("is launching", string)
    return match is not None


def parseValues(string):
    match = re.search(r"value \[([0-9]+)\] with timestamp \[([0-9]+)\]", string)
    return int(match[1]), int(match[2])


def parseChrono(string):
    match = re.search(r"([0-9]+)μs", string)
    return int(match[1])


class Operation:
    def __init__(self, p, op, val, t, start, end, dur, hist):
        self.process = p
        self.operation = op
        self.value = val
        self.timestamp = t
        self.startTime = start
        self.endTime = end
        self.duration = dur
        self.history = hist

    def __str__(self):
        return "p" + str(self.process) + " " + \
            self.operation + " value " + \
            str(self.value) + " with t=" + \
            str(self.timestamp) + " from " + \
            str(self.startTime) + " to " + \
            str(self.endTime) + " in " + \
            str(self.duration) + "μs"

    def getLastPut(self):
        lastPut = None
        for x in self.history.timeline:
            if x.operation == "put" and (lastPut is None or (x.endTime <= self.startTime and x.endTime > lastPut.endTime)):
                lastPut = x
        return lastPut

    def getConcurrentsPut(self):
        concurrentsPut = []
        for x in self.history.timeline:
            if x.operation == "put" and ((x.startTime <= self.startTime and x.endTime > self.startTime)
            or (x.startTime > self.startTime and x.startTime < self.endTime)):
                concurrentsPut.append(x)
        return concurrentsPut


class History:
    def __init__(self, lines):
        self.timeline = []
        while lines:
            actor = parseActor(lines[0])
            operation = parseOperation(lines[0])
            start = parseTime(lines[0])
            j = 1
            while j < len(lines) and parseActor(lines[j]) != actor:
                j += 1

            # Liveness check
            if j >= len(lines):
                print("Not lively!")
                exit(1)
            value, timestamp = parseValues(lines[j])
            end = parseTime(lines[j])
            duration = parseChrono(lines[j])

            self.timeline.append(Operation(actor, operation, value, timestamp, start, end, duration, self))

            lines.pop(j)
            lines.pop(0)

    def __str__(self):
        string = ""
        for x in self.timeline:
            string += str(x) + "\n"

        return string

    def checkLiveness(self):
        for x in self.timeline:
            if x.operation == "get":
                possibleValues = set()
                lastPut = x.getLastPut()
                possibleValues.add(lastPut.value)
                possibleValues.update([concurrent.value for concurrent in lastPut.getConcurrentsPut()])
                possibleValues.update([concurrent.value for concurrent in x.getConcurrentsPut()])

                # Safety check
                if x.value not in possibleValues:
                    print(x.value, possibleValues)
                    return False
        return True


def exec(N, M):
    with open("command.txt", "r", encoding="utf8") as command:
        cmd = command.read()

    os.system("{} {} {} > logs.txt".format(cmd, N, M))


def main():
    for N in [3, 10, 100]:
        for M in [3, 10, 100]:
            print("Testing with N =", N, "and M =", M)
            exec(N, M)
            with open("logs.txt", "r", encoding="utf8") as logs:
                lines = logs.readlines()[1:]
            history = History(lines)
            print("Lively!")

            if history.checkLiveness():
                print("Safe!")
            else:
                print("Not safe!")
                print(history)
                exit(1)


if __name__ == "__main__":
    main()
