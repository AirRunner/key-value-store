from os import system
from re import search
from datetime import datetime
from statistics import median


## Parsers

def parseTime(string):
    match = search(r"[0-9]{4}-.*(\.|,)[0-9]{1,6}", string)
    if match is None:
        match = search(r"[0-9]{4}-.*:[0-9]{2}", string)
        return datetime.strptime(match[0], "%Y-%m-%d %H:%M:%S")
    return datetime.strptime(match[0], "%Y-%m-%d %H:%M:%S{}%f".format(match[1]))


def parseActor(string):
    match = search(r" p([0-9]+) ", string)
    return int(match[1])


def parseOperation(string):
    match = search(r"put|get|got", string)
    return match[0].replace("got", "get")


def isStarting(string):
    match = search("is launching", string)
    return match is not None


def parseValues(string):
    match = search(r"value \[([0-9]+)\] .* timestamp \[([0-9]+)\]", string)
    return int(match[1]), int(match[2])


def parseChrono(string):
    match = search(r"([0-9]+)μs", string)
    return int(match[1])


## Classes

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
        self.concurrentsPut = []
        self.concurrentsGet = []

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
            if x.operation == "put" and ((lastPut is None and x.endTime <= self.startTime) or (x.endTime <= self.startTime and x.endTime > lastPut.endTime)):
                lastPut = x
        return lastPut

    def getConcurrents(self, op):
        concurrents = []
        for x in self.history.timeline:
            if x.operation == op and ((x.startTime <= self.startTime and x.endTime > self.startTime)
            or (x.startTime > self.startTime and x.startTime < self.endTime)):
                concurrents.append(x)
        return concurrents

    def newOldInversion(self):
        for x in self.concurrentsPut:
            for get in x.concurrentsGet:
                if get.endTime < self.startTime and get.value == x.value:
                    return True
        return False


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

        for x in self.timeline:
            x.concurrentsPut = x.getConcurrents("put")
            if x.operation == "put":
                x.concurrentsGet = x.getConcurrents("get")

    def __str__(self):
        string = ""
        for x in self.timeline:
            string += str(x) + "\n"

        return string

    def checkSafety(self):
        for x in self.timeline:
            if x.operation == "get":
                possibleValues = set()
                lastPut = x.getLastPut()

                possibleValues.update([conc.value for conc in x.concurrentsPut])

                if lastPut is not None and not x.newOldInversion():
                    possibleValues.add(lastPut.value)
                    possibleValues.update([conc.value for conc in lastPut.concurrentsPut])

                # Safety check
                if x.value not in possibleValues:
                    print(x.value, possibleValues)
                    return False
        return True


## Execution functions

def performance(timeline):
    putDurations = []
    getDurations = []

    for x in timeline:
        if x.operation == "put":
            putDurations.append(x.duration)
        else:
            getDurations.append(x.duration)

    return median(putDurations), median(getDurations)


def launch(N, M):
    with open("command.txt", "r", encoding="utf8") as command:
        cmd = command.read()

    system("{} {} {} > logs.txt".format(cmd, N, M))


def main():
    for N in [3, 10, 100]:
        for M in [3, 10, 100]:
            print("Testing with N =", N, "and M =", M)
            launch(N, M)
            with open("logs.txt", "r", encoding="utf8") as logs:
                lines = logs.readlines()[1:]

            latency = (parseTime(lines[-1]) - parseTime(lines[0])).total_seconds()
            history = History(lines)
            print("Lively!")

            if history.checkSafety():
                print("Safe!")
            else:
                print("Not safe!")
                print(history)
                exit(1)

            putDuration, getDuration = performance(history.timeline)
            print("Total computation time:", latency, "sec")
            print("Put median duration:", putDuration, "μs")
            print("Get median duration:", getDuration, "μs")
            print()


if __name__ == "__main__":
    main()
