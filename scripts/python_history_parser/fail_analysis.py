from edn_format import Keyword

# count and display the amount of ok w.r.t ok+fail
def analyse_fails(history):
    okays = len([x for x in history if x[Keyword("type")]==Keyword("ok")])
    fails = len([x for x in history if x[Keyword("type")]==Keyword("fail")])
    invokes = len([x for x in history if x[Keyword("type")]==Keyword("invoke")])
    print('OK: {}% ({}/{})'.format(okays/(okays+fails)*100, okays, fails))
    # return for accumilation acros histries
    return (invokes, okays)