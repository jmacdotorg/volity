from boodle.agent import *
import snazz

class Sample(Agent):
	def run(self):
		ag = snazz.Simple(2)
		fader = FadeInOutAgent(ag, 14, 0.01)
		self.sched_agent(fader)
		