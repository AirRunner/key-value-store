import correctness as cor
from datetime import datetime

line1 = "[2020-11-04 12:31:01.308908400] [INFO] - p0 is launching a put request with key 1 and proposal 0..."
line2 = "[2020-11-04 12:31:01.447535900] [INFO] - p2 got the value [5] with key [1] and timestamp [2] in 6715μs"

with open("test_log1.txt", "r", encoding="utf8") as log1:
    lines1 = log1.readlines()
with open("test_log1.txt", "r", encoding="utf8") as log2:
    lines2 = log2.readlines()

history1 = cor.History(lines1)
history2 = cor.History(lines2)


def test_parseTime():
    timeFormat = "%Y-%m-%d %H:%M:%S.%f"
    assert cor.parseTime(line1) == datetime.strptime("2020-11-04 12:31:01.308908", timeFormat)
    assert cor.parseTime(line2) == datetime.strptime("2020-11-04 12:31:01.447535", timeFormat)


def test_parseActor():
    assert cor.parseActor(line1) == 0
    assert cor.parseActor(line2) == 2


def test_parseOperation():
    assert cor.parseOperation(line1) == "put"
    assert cor.parseOperation(line2) == "get"


def test_isStarting():
    assert cor.isStarting(line1) is True
    assert cor.isStarting(line2) is False


def test_parseValues():
    assert cor.parseValues(line2) == (5, 2)


def test_parseChrono():
    assert cor.parseChrono(line2) == 6715


def test_getLastPut():
    assert history1.timeline[4].getLastPut() == history1.timeline[2]
    assert history1.timeline[1].getLastPut() is None


def test_getConcurrentsPut():
    assert history1.timeline[1].getConcurrentsPut() == [history1.timeline[0], history1.timeline[2]]
    assert history1.timeline[4].getConcurrentsPut() == []


def test_checkSafety():
    assert history1.checkSafety() is True
    assert history2.checkSafety() is False


def test_performance():
    assert cor.performance(history1.timeline) == (90908.5, 3793)
