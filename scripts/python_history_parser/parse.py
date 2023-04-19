import edn_format #pip install edn_format
from edn_format import Keyword
from simple_droped_append import *
from linear_droped_append import *
from fail_analysis import *
from check_order_inversion import *
import os

# parse a history.edn file from jepsen and return it as a list of python dictionarys
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

# return only the successfull actions
def filter_successfull(history):
    return [x for x in history if x[Keyword("type")]==Keyword("ok")]

# print an entire history
def print_values(history):
    for x in history:
        print(x[Keyword("index")], x[Keyword("process")], x[Keyword("value")])

# check if the history in the given order is a correct serial execution
# assumes only success full actions, no invoke, fail, or info, only ok
def verify_this_order(history):
    # key value store for the expected data in the database
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
                    # if the expected value v is not the actual read value, than this order is incorrect
                    return False
    # no incorrect read found
    return True

# verify that append was not called more than once per key, value combination.
# this is to rule out that duplicates in the output were actualy coused by wrong input instead of inconsistencys in the database
def check_duplicate_append_requests(history):
    # only check successfull appends
    commits = [x for x in history if x[Keyword("type")]==Keyword("ok")]

    storage = {}

    # execute all the appends to get all the values that should be in the database
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
        
        # set removes duplicates so if there is a duplicate element in the database and thus in the request the set will be smaller
        if(len(data) != len(set(data))):
            print(key, " has duplicated append request")
            duplicate_found = True
    
    if not duplicate_found:
        print("No duplicated append requests")

def main():

    # parse and analyze the histories in ./store/*/
    # so that you can copy the store folder from jepsen

    # stats we keep track of
    total_append = 0
    total_stale = 0
    total_found = 0
    total_double = 0
    total_invoke = 0
    total_ok = 0
    for dir in os.listdir("store"):
        print(dir)
        history = parse("store/" + dir + "/history.edn")
        
        # check howmanny request were successfull or failed
        (ni, nok) = analyse_fails(history)
        total_invoke += ni
        total_ok += nok
        
        # check that there were no duplicate request
        check_duplicate_append_requests(history)

        # try to find anomolies based on linearizability
        storage = track_linear_droped_append(history)
        (a, s, f, d) = analyse_simple_droped_append(storage)
        total_append += a
        total_stale += s
        total_found += f
        total_double += d
    
    # seems to underestimate lost and overestimate stale, only use total_found as sum of the two : the values where something went wrong
    print()
    print("Total stale reads: ", total_found , " / ", total_append)
    print("Total lost writes: ", total_stale-total_found)
    print("Total doubled writes", total_double)
    print("ok/invoke: ", total_invoke, " / ", total_ok)


if __name__ == "__main__":
    main()