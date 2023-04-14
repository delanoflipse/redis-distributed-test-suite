import edn_format #pip install edn_format
from edn_format import Keyword

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

def analyse_simple_droped_append(storage):
    num_keys = 0;
    lost_total = 0;
    found_total = 0;
    latest_total = 0;
    for key_store in storage.items():
        key = key_store[0]
        data = key_store[1]

        append_count = len(data["append"])
        lost_count = len(data["lost"])
        found_count = len(data["found"])
        latest_count = len(data["latest"])
        latest_no_duplicates = len(set(data["latest"]))
        duplicates = []
        if (latest_count-latest_no_duplicates > 0):
            duplicates = find_duplicates(data["latest"])

        lost_percentage = lost_count / append_count * 100;
        found_percentage = found_count / lost_count * 100 if lost_count != 0 else 100.0;
        latest_percentage = latest_count / append_count * 100;

        print('{} : lost: {}%, recovered: {}%, final contained: {}%, final duplicates: {} {}'.format(key, lost_percentage, found_percentage, latest_percentage, latest_count-latest_no_duplicates, duplicates))

        num_keys += 1
        lost_total += lost_percentage
        found_total += found_percentage
        latest_total += latest_percentage
    
    lost_total /= num_keys
    found_total /= num_keys
    latest_total /= num_keys

    print('average : lost: {}%, recovered: {}%, final contained: {}%'.format(lost_total, found_total, latest_total))