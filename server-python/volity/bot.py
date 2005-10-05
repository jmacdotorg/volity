import logging

class Bot:
    gameclass = None

    def __init__(self, act):
        if (not isinstance(act, actor.Actor)):
            raise TypeError('bot must be initialized with an Actor')
        self.actor = act
        self.log = self.actor.log

    def getname(self):
        if (self.gameclass and self.gameclass.gamename):
            return self.gameclass.gamename + ' Bot'
        return 'Bot'

    def send(self, methname, *args, **keywords):
        self.queueaction(self.performsend, methname, *args, **keywords)
    
    def performsend(self, methname, *args, **keywords):
        if (not '.' in methname):
            methname = 'game.' + methname
        self.actor.sendref(methname, *args, **keywords)
    
    def queueaction(self, op, *args):
        return self.actor.queueaction(op, *args)
        
    def addtimer(self, op, *args, **dic):
        return self.actor.addtimer(op, *args, **dic)

    def begingame(self):
        pass

    def endgame(self):
        pass

    def suspendgame(self):
        pass

    def resumegame(self):
        pass

    def destroy(self):
        pass

# late imports
import game
import actor

class NullBot(Bot):
    gameclass = game.Game

