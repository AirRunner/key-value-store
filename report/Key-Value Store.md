# Key-Value Store with akka actor model

## 1. Description of the system

### 1.A. Processes

The aim of this project is to create a key-value store. To do this, we have implemented a multi-writer multi-reader atomic registers system. The principle is therefore to create a system containing several registers. Each register can perform two theoretical actions: read its local value corresponding to a given key, and write a local value for a given key (hence the name 'key-value').

#### a. Attributes

This system is composed of `N` processes. Each process runs using nine main attributes, as shown below.

- `processes`: all other processes references
- `mailbox`: a mailbox for storing the incoming messages
- `values`: a local key-value hashmap
- `timestamps`: a local key-timestamp hashmap
- `state`: the current process state
- `proposal`: proposed value in `PUT` operations
- `currSeqNumbers`: a list of sequence number of all messages for current operation
- `ackNumber`: the number of received acknownledgements

#### b. States

The process can be into five different states, described below.

- `faulty`: This state simulates a process that failed and cannot respond.
- `get`: The process is in this state all along a `GET` request.
- `put`: The process is in this state when it begins a `PUT` request.
- `wait_write`: At the end of a `PUT` request, the process passes to this state until it receives all write responses.
- `none`: By default, and when no operation is running, the process is in this state.

#### c. Messages

The processes can receive eight different types of messages. At each message processing, the process executes the following operations:

- `Members`: set local processes references
- `Fail`: pass to state `faulty`
- Operations
	- `Get`: launch a `GET` request
	- `Put`: launch a `PUT` request
- Requests and responses
	- `ReadRequest`: launch a read request to all processes
	- `ReadResponse`: process an incoming read response from a previous read request
	- `WriteRequest`: launch a write request to all processes
	- `WriteResponse`: process an incoming write response from a previous write request


### 1.B. Messages processing

The main method of a process is `onReceive()`. It is the method called each time a process receives a message. According to the type of message, the corresponding private method will be called to process it. Let's see how each message is processed.

#### a. Operations

The two possible operations are `GET` and `PUT`, and run as follows:

![](operations_diagram.png)

##### I. Get

A `Get` message only contains the requested key.

When launching a `GET` request, the process passes to `GET` state and just sends `Read` requests to all other processes.  
A read request is sent with the key as well as a sequence number, in order to recognise corresponding responses.

##### II. Put

A `Put` message contains the requested key and a proposal value to write.

When launching a `PUT` request, the process passes to `PUT` state. Then, it first sends `Read` requests with the key to all other processes.

#### b. Requests and responses

##### I. Read request

A `Read` request contains the requested key `key` and the sequence number of the initial request.

##### II. Read response



##### III. Write request



##### IV. Write response

