"""onetwomany -- an implementation of One Two Many
    Implemented by Andrew Plotkin.

Ruleset URI: <http://eblong.com/zarf/volity/ruleset/onetwomany/OneTwoMany.html>
"""

from zymb import jabber
import zymb.jabber.rpc
import volity.game

NUMSEATS = 15

class OneTwoMany(volity.game.Game):
    
    gamename = 'One Two Many'
    gamedescription = 'A word-guessing party game'
    ruleseturi = 'http://eblong.com/zarf/volity/ruleset/onetwomany/OneTwoMany.html'
    rulesetversion = '1.0'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('guess', afoot=True, seated=True, args=[int, str])
        self.validatecalls('equivalent', afoot=True, seated=True, args=[bool])
        
        # Set up the seats.
        for ix in range(1, NUMSEATS+1):
            volity.game.Seat(self, 'seat'+str(ix), False)

        self.round = None
        self.guess1 = None
        self.guess2 = None
        self.guesser1 = None
        self.guesser2 = None
        self.guesses = None
        self.vote_done = None

    def destroy(self):
        """Tear down the game object.
        """
        self.guesser1 = None
        self.guesser2 = None
        self.guesses = None
        self.vote_done = None

    def sendgamestate(self, player, playerseat):
        """Send the game state information.
        """
        
        if (self.round == 0):
            player.send('begin_initial')
        else:
            player.send('game.begin_guessing', self.round,
                self.guesser1, self.guess1,
                self.guesser2, self.guess2)

        if (self.getstate() != volity.game.STATE_SUSPENDED):
            if (playerseat and self.guesses.has_key(playerseat.id)):
                player.send('game.your_guess_is', self.guesses[playerseat.id])
                    
        for seat in self.guesses.keys():
            player.send('game.guessed', seat)
        for seat in self.vote_done.keys():
            player.send('game.voted_equivalent', seat, True)
    
    def checkseating(self):
        """Make sure at least two players are present.
        """
        ls = [ seat for seat in self.getseatlist()
               if not seat.isempty() ]
        if (len(ls) < 2):
            raise volity.game.FailureToken('game.need_min_players')
        
    def begingame(self):
        """Begin-game handler.
        """

        self.guess1 = ''
        self.guess2 = ''
        self.guesser1 = None
        self.guesser2 = None
        
        self.round = 0
        self.guesses = {}
        self.vote_done = {}
        
        self.sendtable('begin_initial')
        
    def endgame(self, cancelled):
        """End-game handler.
        """

        self.round = None
        self.guess1 = None
        self.guess2 = None
        self.guesser1 = None
        self.guesser2 = None
        self.guesses = None
        self.vote_done = None

    def suspendgame(self):
        """Suspend-game handler.
        """
        self.vote_done.clear()
        self.guesses.clear()
        
    def new_round(self):
        """Check the two guesses, and either start a new round or end the
        game.
        """

        ls = self.guesses.keys()
        assert (len(ls) == 2)

        self.guesser1 = self.getseat(ls[0])
        self.guesser2 = self.getseat(ls[1])
        self.guess1 = self.guesses[self.guesser1.id]
        self.guess2 = self.guesses[self.guesser2.id]

        if (self.guess1.lower() == self.guess2.lower()):
            self.wingame()
            return

        self.vote_done.clear()
        self.guesses.clear()
        self.round += 1
        
        self.sendtable('game.begin_guessing', self.round,
            self.guesser1, self.guess1,
            self.guesser2, self.guess2)

    def wingame(self):
        self.sendtable('game.win',
            self.guesser1, self.guess1,
            self.guesser2, self.guess2)
        self.gameover([self.guesser1, self.guesser2])
        
    # The following methods are RPC handlers.
                
    def rpc_guess(self, sender, round, guess):
        seat = self.getplayerseat(sender)
        
        if (round != self.round):
            raise volity.game.FailureToken('game.wrong_round')
        guess = guess.strip()

        def func(self):
            self.log.error('### guesses: %s', repr(self.guesses))
        self.queueaction(func, self) ###
        
        if (not guess):
            if (self.guesses.has_key(seat.id)):
                # Player has cancelled his guess.
                self.guesses.pop(seat.id)
                seat.send('your_guess_is', '')
                self.sendtable('guessed', '')
            return

        if (self.guesses.has_key(seat.id)):
            # Player has replaced his previous guess. No RPC sent.
            self.guesses[seat.id] = guess
            seat.send('your_guess_is', guess)
            return

        # New guess.
        self.guesses[seat.id] = guess
        seat.send('your_guess_is', guess)

        if (len(self.guesses) < 2):
            self.sendtable('guessed', seat)
            return

        # Two guesses are in.
        self.queueaction(self.new_round)
        
    def rpc_equivalent(self, sender, val):
        seat = self.getplayerseat(sender)

        if (self.round == 0):
            raise volity.game.FailureToken('game.not_initial')

        def func(self):
            self.log.error('### votes: %s', repr(self.vote_done))
        self.queueaction(func, self) ###
        
        if ((not self.vote_done.has_key(seat.id)) and val):
            self.vote_done[seat.id] = True
            self.sendtable('game.voted_equivalent', seat, True)
        elif (self.vote_done.has_key(seat.id) and (not val)):
            self.vote_done.pop(seat.id)
            self.sendtable('game.voted_equivalent', seat, False)

        if (2 * len(self.vote_done) > len(self.getgameseatlist())):
            self.queueaction(self.wingame)
