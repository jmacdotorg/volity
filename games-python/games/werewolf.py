"""werewolf -- an implementation of Werewolf (Mafia)
    Implemented by Andrew Plotkin.

Ruleset URI: <http://volity.org/games/werewolf/>
Game URL:    <http://volity.org/games/werewolf/about.html>
"""

import random
from zymb import jabber
import zymb.jabber.rpc
import volity.game

### add a timer option?

NUMSEATS = 24
ALLOW_LYNCH_PASS = False
MIN_SEATS = 5
DAY = 'day'
NIGHT = 'night'

class PassClass:
    id = 'pass'
Pass = PassClass()  # object which represents "I vote to do nothing"

class Werewolf(volity.game.Game):

    gamename = 'Werewolf'
    gamedescription = 'A social game of deception and murder'
    ruleseturi = 'http://volity.org/games/werewolf/'
    rulesetversion = '1.0'
    websiteurl = 'http://volity.org/games/werewolf/about.html'

    def __init__(self, ref):
        volity.game.Game.__init__(self, ref)

        self.setopset(self)
        self.validatecalls('change_role_count', state=volity.game.STATE_SETUP,
            args=[str, int])
        self.validatecalls('select', afoot=True, seated=True, args=[str])
        self.validatecalls('debug_quit', afoot=True,
            admin=True)

        # Set up the seats.
        for ix in range(1, NUMSEATS+1):
            GameSeat(self, 'seat'+str(ix))

        # Set up the table of roles. These are listed in the Role class
        # (below). We will keep a dict mapping ids to Role objects.
        self.roles = {}
        self.rolelist = []
        for id in Role.rolenames:
            role = Role(self, id)
            self.roles[id] = role
            setattr(self, 'role_'+id, role)
            self.rolelist.append(role)

        self.sanctuary = None
        self.phase = None

    def destroy(self):
        """Tear down the game object.
        """
        self.roles = None
        self.rolelist = None

    def sendconfigstate(self, player):
        """Send the table configuration information. This does not include
        game state -- only what was set up during configuration.
        """

        for role in self.rolelist:
            player.send('role_count', role, role.count)

    def sendgamestate(self, player, playerseat):
        """Send the game state information.
        """

        if (self.getstate() != volity.game.STATE_SUSPENDED):
            if (playerseat):
                role = playerseat.role
                if (role == self.role_fool):
                    role = self.role_seer
                player.send('reveal', playerseat, 'self', role)
                if (role.knowsmembership):
                    for seat2 in role.seats:
                        if (playerseat != seat2):
                            player.send('reveal', seat2, 'role', role)

        # Repeat any seer visions which have occurred.
        if (playerseat):
            for id in playerseat.reveals.keys():
                player.send('reveal', id, 'vision', playerseat.reveals[id])

        # Repeat any death notices
        for seat in self.getgameseatlist():
            if (not seat.isalive):
                role = seat.role
                if (role == self.role_fool):
                    role = self.role_seer
                player.send('died', seat, seat.deathreason, role)

        # Announce the time of day
        if (self.phase == DAY):
            player.send('phase', 'day')
        if (self.phase == NIGHT):
            player.send('phase', 'night')

        if (self.sanctuary):
            player.send('sanctuary', self.sanctuary)

        # Repeat selected calls
        if (self.phase == DAY):
            for seat in self.getgameseatlist():
                if (seat.choice):
                    player.send('selected', seat, seat.choice)
        if (self.phase == NIGHT):
            for seat in self.getgameseatlist():
                share = False
                if (seat == playerseat):
                    share = True
                else:
                    share = (playerseat and (seat.role == playerseat.role)
                        and seat.role.iscollab)
                if (seat.choice and share):
                    player.send('selected', seat, seat.choice)
                        
    def checkconfig(self):
        """Check whether we have a playable table configuration.
        """

        ls = [ seat for seat in self.getseatlist()
            if not seat.isempty() ]
        seatcount = len(ls)
        if (seatcount < MIN_SEATS):
            raise volity.game.FailureToken('game.need_min_players')

        # Recheck the villager count, just in case
        self.recomputevillagers()
        villagers = self.role_villager.count
        total = sum([ role.count for role in self.rolelist ])

        assert (seatcount <= total)
        if (seatcount < total):
            raise volity.game.FailureToken('game.not_enough_players')
        if (self.role_werewolf.count == 0):
            raise volity.game.FailureToken('game.not_enough_wolves')
        innocents = (total
            - (self.role_werewolf.count + self.role_warlock.count))
        if (innocents == 0):
            raise volity.game.FailureToken('game.not_enough_innocents')
        if (self.role_werewolf.count >= innocents):
            raise volity.game.FailureToken('game.too_many_wolves')

        if (self.role_fool.count > 0 and self.role_seer.count == 0):
            raise volity.game.FailureToken('game.no_fool_without_seer')
        if (self.role_granger.count == 1):
            raise volity.game.FailureToken('game.not_1_granger')

    def begingame(self):
        """Begin-game handler.
        """
        
        # Figure out how many villagers we have. For paranoia's sake, we
        # don't rely on the old role_villager.count value.
        
        ls = self.getgameseatlist()
        total = sum([ role.count for role in self.rolelist
            if (role != self.role_villager) ])
        self.role_villager.count = len(ls) - total
        assert self.role_villager.count >= 0

        # Assign the roles.
        
        ls = []
        for role in self.rolelist:
            role.seats = []
            ls.extend([role] * role.count)
        assert len(ls) == len(self.getgameseatlist())

        random.shuffle(ls)
        
        for seat in self.getgameseatlist():
            seat.role = ls.pop()
            seat.role.seats.append(seat)
            seat.isalive = True
            seat.deathreason = None
            seat.reveals = {}

        self.sanctuary = None
        
        # Notify the players of their roles
        for seat in self.getgameseatlist():
            role = seat.role
            if (role == self.role_fool):
                role = self.role_seer
            seat.send('reveal', seat, 'self', role)

        # Notify the roles that know each other's identities
        for role in self.rolelist:
            if (role.knowsmembership):
                for seat in role.seats:
                    for seat2 in role.seats:
                        if (seat != seat2):
                            seat.send('reveal', seat2, 'role', role)

        # Give all the visionaries a free peek
        for seat in self.getgameseatlist():
            if (seat.role in
                [self.role_seer, self.role_fool, self.role_warlock]):
                ls = self.getgameseatlist()
                ls.remove(seat)
                seat2 = random.choice(ls)
                self.givevision(seat, seat2)

        if (self.checkwinner()):
            return
        self.queueaction(self.newday)

    def endgame(self, cancelled):
        """End-game handler.
        """
        
        self.phase = None
        self.sanctuary = None
        for role in self.rolelist:
            role.seats = None
            role.choice = None
        for seat in self.getseatlist():
            seat.role = None
            seat.isalive = None
            seat.deathreason = None
            seat.reveals = {}

        self.queueaction(self.recomputevillagers)

    def unsuspendgame(self):
        """Resume-game handler.
        """
        for pla in self.getplayerlist():
            self.sendgamestate(pla, pla.seat)

    def seatchange(self, player, seat):
        """Handler for players sitting and standing.
        """
        if (self.getstate() == volity.game.STATE_SETUP):
            self.queueaction(self.recomputevillagers)
        
    def recomputevillagers(self):
        """Decide how many villagers we are currently set up for, based on
        the number of occupied seats and the number of other roles configured.
        This will never be negative.
        """
        ls = [ seat for seat in self.getseatlist()
            if not seat.isempty() ]
        total = sum([ role.count for role in self.rolelist
            if (role != self.role_villager) ])
        villagers = len(ls) - total
        if (villagers < 0):
            villagers = 0

        role = self.role_villager
        if (villagers == role.count):
            return
        role.count = villagers
        self.sendtable('role_count', role, role.count)
            
    def newday(self):
        """Invoked when a new day begins.
        """
        
        self.phase = DAY
        # Sanctuary is already set.
        self.sendtable('phase', 'day')
        if (self.sanctuary):
            self.sendtable('sanctuary', self.sanctuary)
        
        for seat in self.getgameseatlist():
            seat.choice = None
        for role in self.rolelist:
            role.choice = None

    def newnight(self):
        """Invoked when a new night begins.
        """
        
        self.phase = NIGHT
        self.sanctuary = None
        self.sendtable('phase', 'night')

        for seat in self.getgameseatlist():
            seat.choice = None
        for role in self.rolelist:
            role.choice = None

    def choicetable(self, ls):
        """choicetable(ls) -> (dict, int)

        Given a list of seats, construct a table which represents the choices
        of those seats. The table maps IDs (of chosen seats) to the number
        of times that seat was chosen. The second part of the result tuple
        is the number of seats which have not made a choice.
        """
        
        choices = {}
        undecided = 0
        for seat in ls:
            choice = seat.choice
            if (choice):
                count = choices.get(choice.id, 0)
                choices[choice.id] = count+1
            else:
                undecided = undecided+1
                
        return (choices, undecided)

    def checkdayover(self):
        """During a day phase, check to see whether the day should end.
        (That is, check whether a majority of players have agreed on a
        lynching victim.) If so, carry out the action.
        """
        
        ls = [ seat for seat in self.getgameseatlist() if seat.isalive ]
        needed = (len(ls)+2) // 2

        (choices, undecided) = self.choicetable(ls)

        results = [ id for id in choices.keys() if choices[id] >= needed ]
        assert len(results) <= 1

        if (not results):
            return
            
        id = results[0]
        if (id == Pass.id):
            seat = Pass
        else:
            seat = self.getseat(id)

        self.killseat(seat, 'villager')
            
        if (self.checkwinner()):
            return
        self.queueaction(self.newnight)

    def checknightover(self):
        """During a night phase, check to see whether the night should end.
        (That is, whether all the players who have nighttime decisions have
        made them.) If so, carry out the action.
        """
        
        for role in self.rolelist:
            if (not role.hasnightwork):
                continue
            if (not role.getlive()):
                continue
                
            if (role.issolo):
                for seat in role.getlive():
                    if (not seat.choice):
                        return
            if (role.iscollab):
                ls = [ seat for seat in role.getlive() ]
                (choices, undecided) = self.choicetable(ls)
                if (undecided):
                    return
                if (len(choices) != 1):
                    return
                id = choices.keys()[0]
                if (id == Pass.id):
                    role.choice = Pass
                else:
                    role.choice = self.getseat(id)

        # Night is over; do everything

        # Seers see
        for seat in self.getgameseatlist():
            if (seat.isalive and (seat.role in
                [self.role_seer, self.role_fool, self.role_warlock])):
                if (seat.choice and seat.choice != Pass):
                    self.givevision(seat, seat.choice)

        # Handle wolves (and herbalist, wolfsbane)
        wolfkill = Pass

        if (self.role_werewolf.getlive()):
            wolfkill = self.role_werewolf.choice

        if (wolfkill != Pass):
            if ((wolfkill.role == self.role_wolfsbane)
                or (self.role_herbalist.getlive()
                    and self.role_herbalist.choice == wolfkill)):
                wolfkill = Pass
        
        assert wolfkill
        self.killseat(wolfkill, 'werewolf')

        if (self.checkwinner()):
            return
            
        # Handle slayer (unless he just got killed)
        slayerkill = Pass

        if (self.role_slayer.getlive()):
            slayerkill = self.role_slayer.choice

        assert slayerkill
        if (slayerkill != Pass and slayerkill.isalive):
            self.killseat(slayerkill, 'slayer')

        if (self.checkwinner()):
            return
            
        # Handle priest (unless he just got killed)
        
        protected = Pass
        if (self.role_priest.getlive()):
            protected = self.role_priest.choice

        assert protected            
        if (protected == Pass or (not protected.isalive)):
            protected = None
        self.sanctuary = protected

        if (self.checkwinner()):
            return
        self.queueaction(self.newday)
            
    def checkwinner(self):
        """Check whether the game has ended. If so, set up the game-ending
        action and return True.

        This must be called every time a player dies.
        """
        
        wolfcount = len(self.role_werewolf.getlive())
        ls = [ len(role.getlive()) for role in self.rolelist
            if (not role.isevil) ]
        goodcount = sum(ls)
        
        if (wolfcount == 0):
            self.queueaction(self.announcewinner, False)
            return True
        if (wolfcount >= goodcount):
            self.queueaction(self.announcewinner, True)
            return True

        return False

    def announcewinner(self, evilwin):
        """Announce which team won, and end the game.
        """
        
        # Announce the real roles.
        for seat in self.getgameseatlist():
            role = seat.role
            self.sendtable('reveal', seat, 'end', role)
        
        # Send out the win message.
        if (evilwin):
            self.sendtable('win', 'werewolf')
        else:
            self.sendtable('win', 'villager')
        
        ls = [ seat for seat in self.getgameseatlist()
            if (seat.role.isevil == evilwin) ]
        # All listed seats tie for first; everyone else ties for last.
        self.gameover(ls)

    def givevision(self, seat, targetseat):
        """Cause *seat* to have a vision of *targetseat*'s role. If *seat*
        is a fool, this may be an incorrect vision.
        """
        
        if (seat.role != self.role_fool):
            role = targetseat.role
        else:
            ls = self.getgameseatlist()
            ls.remove(seat)
            role = random.choice(ls).role
            if (role == self.role_seer):
                role = self.role_fool
        seat.reveals[targetseat.id] = role.id
        seat.send('reveal', targetseat, 'vision', role)

    def killseat(self, seat, reason):
        """Declare the sad passing of *seat*. The *reason* may be 'villager'
        (lynching), 'werewolf', or 'slayer'.
        """
        
        if (seat == Pass):
            self.sendtable('no_deaths', reason)
        else:
            seat.isalive = False
            seat.deathreason = reason
            role = seat.role
            if (role == self.role_fool):
                role = self.role_seer
            self.sendtable('died', seat, reason, role)

    # The following methods are RPC handlers.
                
    def rpc_change_role_count(self, sender, roleid, count):
        role = self.roles.get(roleid)
        if (not role):
            raise volity.game.FailureToken('game.invalid_role')
        if (count < 0 or count > len(self.getseatlist())):
            raise volity.game.FailureToken('game.invalid_role_count')
        if (role == self.role_villager):
            raise volity.game.FailureToken('game.cannot_set_villager_count')
            
        role.count = count
        self.sendtable('role_count', role, role.count)
        self.recomputevillagers()
        self.unready()

    def rpc_select(self, sender, seatid):
        playerseat = self.getplayerseat(sender)
        assert playerseat
        if (not playerseat.isalive):
            raise volity.game.FailureToken('game.no_dead_vote')

        if (not seatid):
            seat = None  # clear selection
        elif (seatid == Pass.id):
            seat = Pass  # select doing nothing
        else:
            seat = self.getseat(seatid)
            if ((not seat) or (not seat.isingame())):
                raise volity.game.FailureToken('game.invalid_seat')
            if (not seat.isalive):
                raise volity.game.FailureToken('game.not_dead')

        if (self.phase == DAY):
            if (seat == Pass and (not ALLOW_LYNCH_PASS)):
                raise volity.game.FailureToken('game.must_lynch')
            if (seat == playerseat):
                raise volity.game.FailureToken('game.not_self')
            if (seat and (seat == self.sanctuary)):
                raise volity.game.FailureToken('game.has_sanctuary')
            playerseat.choice = seat
            self.sendtable('selected', playerseat, (seat or ''))
            self.queueaction(self.checkdayover)
            return

        if (self.phase == NIGHT):
            role = playerseat.role
            if (not role.hasnightwork):
                raise volity.game.FailureToken('game.nothing_to_do')
            if (not (role == self.role_priest
                    or role == self.role_herbalist)):
                if (seat == playerseat):
                    raise volity.game.FailureToken('game.not_self')
                if (seat and (seat != Pass)
                    and role.iscollab and (seat.role == playerseat.role)):
                    raise volity.game.FailureToken('game.not_cohort', role.id)
            playerseat.choice = seat
            if (role.iscollab):
                role.send('selected', playerseat, (seat or ''))
            else:
                playerseat.send('selected', playerseat, (seat or ''))
            self.queueaction(self.checknightover)
            return

        raise jabber.rpc.RPCFault(608, 'select call accepted in illegal game phase')
            
    def rpc_debug_quit(self, sender):
        self.gameover()
    
    def makerpcvalue(self, val):
        """ This allows us to pass a Role object to an RPC-sending function.
        """
        if (val == Pass):
            return Pass.id
        if (isinstance(val, Role)):
            return val.id

class Role:
    """Role: Represents one class of players (villagers, werewolves, etc)
    at a table.

    During configuration, the Role object for a class keeps track of how
    many seats that class will have. (E.g., how many werewolves.) During
    play, the Role also keeps track of which seats they are.
    """
    
    rolenames = [
        'villager', 'werewolf', 'seer', 'warlock', 'fool',
        'wolfsbane', 'herbalist', 'slayer', 'priest', 'granger',
    ]

    evilroles = [ 'werewolf', 'warlock' ]
    nightworkroles = [ 'werewolf', 'seer', 'warlock', 'fool',
        'herbalist', 'slayer', 'priest' ]
    soloworkroles = [ 'seer', 'warlock', 'fool' ]
    knowsmembershiproles = [ 'werewolf', 'granger',
        'herbalist', 'slayer', 'priest', ]

    def __init__(self, game, id):
        self.game = game
        self.id = id
        self.isevil = (id in self.evilroles)
        self.hasnightwork = (id in self.nightworkroles)
        self.issolo = (id in self.soloworkroles)
        self.iscollab = (self.hasnightwork and (not self.issolo))
        self.knowsmembership = (id in self.knowsmembershiproles)
        self.count = 0
        if (self.id == 'werewolf'):
            self.count = 1
        self.seats = None
        self.choice = None

    def getlive(self):
        """getlive() -> list

        Return a list of the living seats which have this role.
        """
        ls = [ seat for seat in self.seats if seat.isalive ]
        return ls
        
    def send(self, methname, *args, **keywords):
        """send(methname, *args, **keywords)

        Send an RPC to each seat which has this role. See the description of
        the sendseat() method in the Game class.
        """
        if (self.seats):
            for seat in self.seats:
                seat.send(methname, *args, **keywords)
        
    def __repr__(self):
        return '<Role \'' + self.id + '\'>'
    def __str__(self):
        return self.id
            
class GameSeat(volity.game.Seat):
    """GameSeat: A subclass of Seat which is customized for Werewolf games.
    """
    
    def __init__(self, game, id):
        volity.game.Seat.__init__(self, game, id, False)
        self.role = None
        self.isalive = None
        self.deathreason = None
        self.reveals = None
        self.choice = None
