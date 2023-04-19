from edn_format import Keyword

# simulate the running of the database based on the history it generated
# while looking for unexpected/inconsistent behavour
def track_linear_droped_append(history):
    # key value store to simulate the database and keep track of other data for each key in the db
    storage = {}

    for commit in history:
        # checks and setups for ok items in the history
        if commit[Keyword("type")]==Keyword("ok"):

            transaction = commit[Keyword("value")]
            for action in transaction:
                # execute the append
                if action[0] == Keyword("append"):
                    # excute the append, if the key is not initialized initialize it
                    if action[1] in storage:
                        storage[action[1]]["append"].append(action[2])
                    else:
                        # append : all append should have been done up till now
                        # lost   : all values that at one point should have been read but were not
                        # found  : all values that were lost, but were later read again
                        # latest : values read on latest read for this key
                        # invoke_append: values in ["append"] when the last read for this key was invoked (should also be by process)
                        # early_read   : values that have been read, but not yet appended, intended to check how trust whorthy the order of this history is, since this should not happen
                        storage[action[1]] = {"append":[action[2]], "lost":[], "found":[], "latest":[], "invoke_append":[], "early_read":[]}
                
                # check the result of the read
                else: # Keyword("r")
                    # get values default is []
                    values = []
                    lost = []
                    found = []
                    early_read = []
                    minimal_values = []
                    if action[1] in storage:
                        values = storage[action[1]]["append"]
                        minimal_values = storage[action[1]]["invoke_append"]
                        lost = storage[action[1]]["lost"]
                        found = storage[action[1]]["found"]
                        early_read = storage[action[1]]["early_read"]

                    
                    read = action[2] # list that was read by the client
                    drops = [v for v in minimal_values if v not in read and (v not in lost)] # find newly dropped values
                    returned = [v for v in minimal_values if v in lost] # check if values showed back up
                    lost.extend(drops)
                    found.extend(returned)
                    early_read.extend([r for r in read if r not in values])

                    # update latest
                    if action[1] in storage:
                        storage[action[1]]["latest"] = read

    
        if commit[Keyword("type")]==Keyword("invoke"):
            transaction = commit[Keyword("value")]
            for action in transaction:
                if action[1] in storage:
                    if action[0] == Keyword("r"):
                        # store the minimal set values this read can see
                        storage[action[1]]["invoke_append"] = storage[action[1]]["append"].copy()
                        
        
    # return for analysis
    return storage