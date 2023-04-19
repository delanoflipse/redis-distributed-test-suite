from edn_format import Keyword

# not compleated
def check_order_inversion(history):

    storage = {}

    for commit in history:
        if commit[Keyword("type")]==Keyword("ok"):
            transaction = commit[Keyword("value")]
            for action in transaction:
                if action[0] == Keyword("r"):
                    key = action[1]
                    value = action[2]
                    if key not in storage:
                        storage[key] = []
                    storage[key].append(value)
    
    for key_store in storage.items():
        key = key_store[0] 
        reads = key_store[1] # lists of read lists
        print(key, reads)
        
        #TODO: check for inversions between elemnets of lists in reads list