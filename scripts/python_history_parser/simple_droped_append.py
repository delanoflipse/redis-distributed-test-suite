import edn_format #pip install edn_format
from edn_format import Keyword

# outdated use track_linear_droped_append
def track_simple_droped_append(history):
    # filter only success
    commits = [x for x in history if x[Keyword("type")]==Keyword("ok")]

    storage = {}

    for commit in commits:
        transaction = commit[Keyword("value")]
        for action in transaction:
            if action[0] == Keyword("append"):
                if action[1] in storage:
                    storage[action[1]]["append"].append(action[2])
                else:
                    storage[action[1]] = {"append":[action[2]], "lost":[], "found":[], "latest":[]}
            else:
                values = []
                lost = []
                found = []
                if action[1] in storage:
                    values = storage[action[1]]["append"]
                    lost = storage[action[1]]["lost"]
                    found = storage[action[1]]["found"]

                
                read = action[2]
                drops = [v for v in values if v not in read and (v not in lost)]
                returned = [v for v in values if v in lost]
                lost.extend(drops)
                found.extend(returned)

                if action[1] in storage:
                    storage[action[1]]["latest"] = read

                
                # clould also check mystery reads
    
    return storage

def find_duplicates(mylist):
#https://www.trainingint.com/how-to-find-duplicates-in-a-python-list.html
    newlist = [] # empty list to hold unique elements from the list
    duplist = [] # empty list to hold the duplicate elements from the list
    for i in mylist:
        if i not in newlist:
            newlist.append(i)
        else:
            duplist.append(i) # this method catches the first duplicate entries, and appends them to the list
    
    return duplist

# analyze results of track_simple_droped_append and track_linear_droped_append
def analyse_simple_droped_append(storage):
    # stats to track
    num_keys = 0;
    total_appends = 0;
    lost_total = 0;
    total_lost_count = 0;
    found_total = 0;
    total_found_count = 0;
    latest_total = 0;
    total_duplicates_count = 0;

    for key_store in storage.items():
        key = key_store[0]
        data = key_store[1]

        # get number of elemenst in each list
        append_count = len(data["append"])
        lost_count = len(data["lost"])
        found_count = len(set(data["found"]))
        latest_count = len(data["latest"])
        latest_no_duplicates = len(set(data["latest"]))
        duplicates = []

        # only get duplicates when we know thay are there
        if (latest_count-latest_no_duplicates > 0):
            duplicates = find_duplicates(data["latest"])

        # calculate results as persentages
        lost_percentage = lost_count / append_count * 100;
        found_percentage = found_count / lost_count * 100 if lost_count != 0 else 100.0;
        latest_percentage = latest_count / append_count * 100;

        #display results
        print('{} : lost: {}%, recovered: {}%, final contained: {}%, final duplicates: {} {}, found: {}'.format(key, lost_percentage, found_percentage, latest_percentage, latest_count-latest_no_duplicates, duplicates, set(data["found"])))

        # acumulate results
        num_keys += 1
        total_appends += append_count
        lost_total += lost_percentage
        total_lost_count += lost_count
        found_total += found_percentage
        total_found_count += found_count
        latest_total += latest_percentage
        total_duplicates_count += latest_count-latest_no_duplicates
    
    # sum to average
    lost_total /= num_keys
    found_total /= num_keys
    latest_total /= num_keys

    # displayed total/average results
    print('average : lost: {}%, recovered: {}%, final contained: {}%'.format(lost_total, found_total, latest_total))

    # return data for accumilation over multyple histories
    return (total_appends, total_lost_count, total_found_count, total_duplicates_count)