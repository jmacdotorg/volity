#!/usr/bin/python

"""readln.py -- accept input from readline.

(Written by Andrew Plotkin. This script is in the public domain.
See <http://www.eblong.com/zarf/zymb/> for more information.)

This is an example which uses readlineagent.Readline. It creates a
readlineagent.Readline agent, which accepts data from readline --
with all the usual readline editing -- and prints what you type.
Empty inputs are ignored. (I.e, just pounding Enter will not print empty
lines.)

To show off a readlineagent features, a few inputs are taken as special.
Type the single word (with no punctuation) to trigger the effect.

    prompt: In addition to printing "prompt", this changes the input
        prompt to "=>". You can repeat this for more equals signs.
    time: In addition to printing "time", this queues up a delayed event.
        After one second, the line "done." will be printed. If you are
        in the middle of typing another line when the "done" appears,
        your input will not be corrupted.
    exit: In addition to printing "exit", this shuts down the agent.
        (You can also interrupt the agent by hitting ctrl-C.)

PLEASE NOTE:        
    This agent WILL NOT WORK without a hacked-up Python readline module.
    The standard Python readline module is missing some APIs which are
    necessary for asynchronous operation. You can get my hacked-up source
    from <http://www.eblong.com/zarf/zymb/>.
"""

from zymb import readlineagent

# Create the agent.
cl = readlineagent.Readline(True)

def handle(input):
    """handle -- 'input' event handler. This is invoked when the agent
    receives a line of input; which is to say, when the user hits
    Enter.
    """

    # Readline input comes with a trailing newline. Strip that, and any
    # whitespace.
    input = input.strip()
    
    if (input == 'prompt'):
        newprompt = '=' + cl.getprompt()
        cl.setprompt(newprompt)
    if (input == 'time'):
        cl.addtimer(cl.write, 'done.\n', delay=1)
    if (input == 'exit'):
        cl.stop()

    # Lines send to cl.write must end with a newline.
    cl.write(input + '\n')

# Add a handler to stop the agent when ctrl-C is hit.
cl.addhandler('interrupt', cl.stop)

# Add a handler to print the lines that are received (and treat a few of
# them specially.)
cl.addhandler('input', handle)

# Tell the agent to begin work.
cl.start()

# The main doing-stuff loop. This is different from the other examples,
# because the readline agent is special.
readlineagent.mainloop()
