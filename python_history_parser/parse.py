import edn_format #pip install edn_format
from edn_format import Keyword
from simple_droped_append import *
from linear_droped_append import *
from fail_analysis import *
from check_order_inversion import *

def parse(file_name):
    inputfile = open(file_name)
    
    print("start parsing")
    
    history = []
    for line in inputfile.readlines():
        edn_line = edn_format.loads(line)
        if edn_line is not None:
            history.append(edn_line)
    
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

def check_duplicate_append_requests(history):
    commits = [x for x in history if x[Keyword("type")]==Keyword("ok")]

    storage = {}

    for commit in commits:
        transaction = commit[Keyword("value")]
        for action in transaction:
            if action[0] == Keyword("append"):
                # execute the append
                if action[1] in storage:
                    storage[action[1]].append(action[2])
                else:
                    storage[action[1]] = [action[2]]
    
    duplicate_found = False
    
    for key_store in storage.items():
        key = key_store[0]
        data = key_store[1]
        
        if(len(data) != len(set(data))):
            print(key, " has duplicated append request")
            duplicate_found = True
    
    if not duplicate_found:
        print("No duplicated append requests")

def main():

    #history = parse("test_history.edn")
    history = parse("histories/history_redis_new.edn")
    analyse_fails(history)
    check_duplicate_append_requests(history)
    storage = track_linear_droped_append(history)
    #print(storage)
    analyse_simple_droped_append(storage)
    #check_order_inversion(history)

    return 0

    history = parse('history.edn')
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