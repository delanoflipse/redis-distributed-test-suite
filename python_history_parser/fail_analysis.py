from edn_format import Keyword

def analyse_fails(history):
    okays = len([x for x in history if x[Keyword("type")]==Keyword("ok")])
    fails = len([x for x in history if x[Keyword("type")]==Keyword("fail")])
    print('OK: {}% ({}/{})'.format(okays/(okays+fails)*100, okays, fails))