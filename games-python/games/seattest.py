"""seattest -- a collection of Volity non-games.

These "games" exist only to exercise the Volity client for various
seating arrangements. Once the game starts, they only support one
RPC: "game.win", which a client sends in order to win the game.
"""

from zymb import jabber
import zymb.jabber.rpc
import volity.game

# Games that allow "any number of players" will still have a maximum
# number of seats. Twenty or thirty is a reasonable limit; a very social
# game can use a higher limit.
#
# However, this module uses an unnaturally low limit, to make client 
# testing easier. To wit: eight.
#
MAXPLAYERS = 8

class TwoPlayer(volity.game.Game):
    gamename = 'Seat-testing: two players'
    gamedescription = 'Exactly two seats, must be filled'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/seattest/TwoPlayer.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('win', afoot=True, seated=True, argcount=0)
        
        volity.game.Seat(self, 'white')
        volity.game.Seat(self, 'black')

    def rpc_win(self, sender, *args):
        self.gameover(self.getplayerseat(sender))

class FourPlayer(volity.game.Game):
    gamename = 'Seat-testing: four players'
    gamedescription = 'Exactly four seats, must be filled'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/seattest/FourPlayer.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('win', afoot=True, seated=True, argcount=0)
        
        volity.game.Seat(self, 'north')
        volity.game.Seat(self, 'south')
        volity.game.Seat(self, 'east')
        volity.game.Seat(self, 'west')

    def rpc_win(self, sender, *args):
        self.gameover(self.getplayerseat(sender))

class AnyNumber(volity.game.Game):
    gamename = 'Seat-testing: any number of players'
    gamedescription = 'Any number of seats, at least one must be filled'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/seattest/AnyNumber.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('win', afoot=True, seated=True, argcount=0)
        
        for ix in range(MAXPLAYERS):
            volity.game.Seat(self, 'seat'+str(ix), False)

    def rpc_win(self, sender, *args):
        self.gameover(self.getplayerseat(sender))

class TwoPlusAnyNumber(volity.game.Game):
    gamename = 'Seat-testing: any number of players, including two captains'
    gamedescription = 'Two captains, and any number of further seats'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/seattest/TwoPlusAnyNumber.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('win', afoot=True, seated=True, argcount=0)

        volity.game.Seat(self, 'captain0')
        volity.game.Seat(self, 'captain1')
        for ix in range(MAXPLAYERS):
            volity.game.Seat(self, 'seat'+str(ix), False)

    def rpc_win(self, sender, *args):
        self.gameover(self.getplayerseat(sender))

class TwoOrMore(volity.game.Game):
    gamename = 'Seat-testing: two or more players'
    gamedescription = 'Any number of seats, at least two must be filled'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/seattest/TwoOrMore.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('win', afoot=True, seated=True, argcount=0)
        
        for ix in range(MAXPLAYERS):
            volity.game.Seat(self, 'seat'+str(ix), False)

    def checkseating(self):
        ls = [ seat for seat in self.getseatlist()
            if not seat.isempty() ]
        if (len(ls) < 2):
            raise volity.game.FailureToken('game.two_seats_needed')

    def rpc_win(self, sender, *args):
        self.gameover(self.getplayerseat(sender))

class AnyNumberPairs(volity.game.Game):
    gamename = 'Seat-testing: even number of players, seated in pairs'
    gamedescription = 'Any even number of players, seated in pairs'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/seattest/AnyNumberPairs.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('win', afoot=True, seated=True, argcount=0)
        
        for ix in range(MAXPLAYERS/2):
            seatn = volity.game.Seat(self, 'seatnorth'+str(ix), False)
            seats = volity.game.Seat(self, 'seatsouth'+str(ix), False)
            seatn.partner = seats
            seats.partner = seatn

    def requestanyseat(self, player):
        ls = [ seat for seat in self.getseatlist()
            if (not seat.isempty()) and seat.partner.isempty() ]
        if (ls):
            return ls[0].partner
        
        ls = [ seat for seat in self.getseatlist()
            if seat.isempty() ]
        if (ls):
            return ls[0]
        return None
    
    def checkseating(self):
        ls = [ seat for seat in self.getseatlist()
            if (not seat.isempty()) and seat.partner.isempty() ]
        if (ls):
            raise volity.game.FailureToken('game.seat_pairs')

    def rpc_win(self, sender, *args):
        self.gameover(self.getplayerseat(sender))
    

class ConfigSeatCount(volity.game.Game):
    gamename = 'Seat-testing: configurable number of seats'
    gamedescription = 'A number of seats configured by the game'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/seattest/ConfigSeatCount.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('config_count', afoot=False, args=int)
        self.validatecalls('win', afoot=True, seated=True, argcount=0)

        self.count = 2
        
        for ix in range(MAXPLAYERS):
            volity.game.Seat(self, 'seat'+str(ix), False)
            
        ls = self.getseatlist()
        self.setseatsrequired(ls[:self.count])

    def requestparticularseat(self, player, seat):
        ls = self.getseatlist()
        legal = ls[:self.count]
        if (not seat in legal):
            raise volity.game.FailureToken('game.illegal_seat',
                seat, self.count)
            
    def requestanyseat(self, player):
        ls = [ seat for seat in self.getseatlist()
            if (seat.isrequired() and seat.isempty()) ]
        if (ls):
            return ls[0]
        return None

    def rpc_config_count(self, sender, *args):
        val = args[0]
        if (val < 1 or val > MAXPLAYERS):
            raise volity.game.FailureToken('game.config_count_bounds',
                MAXPLAYERS)

        ls = self.getseatlist()
        subls = [ seat for seat in ls[val:]
            if not seat.isempty() ]
        if (subls):
            raise volity.game.FailureToken('game.del_occupied_seat')

        self.count = val
        self.unready()
        self.setseatsrequired(ls[:self.count], ls[self.count:])
    
    def rpc_win(self, sender, *args):
        self.gameover(self.getplayerseat(sender))

    