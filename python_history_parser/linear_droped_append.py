from edn_format import Keyword

def track_linear_droped_append(history):
    storage = {}

    for commit in history:
        if commit[Keyword("type")]==Keyword("ok"):

            transaction = commit[Keyword("value")]
            for action in transaction:
                if action[0] == Keyword("append"):
                    if action[1] in storage:
                        storage[action[1]]["append"].append(action[2])
                    else:
                        storage[action[1]] = {"append":[action[2]], "lost":[], "found":[], "latest":[], "invoke_append":[], "early_read":[]}
                     
                else: # Keyword("read")
                    values = []
                    lost = []
                    found = []
                    early_read = []
                    if action[1] in storage:
                        values = storage[action[1]]["append"]
                        minimal_values = storage[action[1]]["invoke_append"]
                        lost = storage[action[1]]["lost"]
                        found = storage[action[1]]["found"]
                        early_read = storage[action[1]]["early_read"]

                    
                    read = action[2]
                    drops = [v for v in minimal_values if v not in read and (v not in lost)]
                    returned = [v for v in minimal_values if v in lost]
                    lost.extend(drops)
                    found.extend(returned)
                    early_read.extend([r for r in read if r not in values])

                    if action[1] in storage:
                        storage[action[1]]["latest"] = read

                                      
                    # clould also check mystery reads
    
        if commit[Keyword("type")]==Keyword("invoke"):
            transaction = commit[Keyword("value")]
            for action in transaction:
                if action[1] in storage:
                    if action[0] == Keyword("r"):
                        storage[action[1]]["invoke_append"] = storage[action[1]]["append"].copy()
                        
        
    
    return storage