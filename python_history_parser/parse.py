import edn_format #pip install edn_format
from edn_format import Keyword

def parse():
    inputfile = open('history.edn')
    
    print("start parsing")
    
    history = []
    for line in inputfile.readlines():
        history.append(edn_format.loads(line))
    
    print("parsing done")
    
    return history

def filter_successfull(history):
    return [x for x in history if x[Keyword("type")]==Keyword("ok")]

def print_values(history):
    for x in history:
        print(x[Keyword("index")], x[Keyword("process")], x[Keyword("value")])

def verify_this_order(history):
    storage = {}
    for x in history:
        transaction = x[Keyword("value")]
        for action in transaction:
            if action[0] == Keyword("append"):
                # execute the append
                if action[1] in storage:
                    storage[action[1]].append(action[2])
                else:
                    storage[action[1]] = [action[2]]
            else:
                # verify the read
                v = []
                if action[1] in storage:
                    v = storage[action[1]]
                if v != action[2]:
                    return False
    #print(storage)
    return True

def main():
    history = parse()
    ok_hist = filter_successfull(history)
    # only requests form node 0 seem to execute successfuly, the others all fail
    
    # check if the transactions that were successfuly could be executed in the given order
    if verify_this_order(ok_hist):
        print("The checked order of the history is okay")
    else:
        print("The checked order of the history is not okay")
        
    # print the: index, process, and value of all lines in the history
    print_values(ok_hist)


if __name__ == "__main__":
    main()