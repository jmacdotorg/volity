-- MySQL dump 10.10
--
-- Host: localhost    Database: volity
-- ------------------------------------------------------
-- Server version	5.0.18-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `game`
--

DROP TABLE IF EXISTS `game`;
CREATE TABLE `game` (
  `id` int(11) NOT NULL auto_increment,
  `start_time` datetime default NULL,
  `end_time` datetime default NULL,
  `server_id` int(11) default NULL,
  `signature` text,
  `ruleset_id` int(11) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `game_seat`
--

DROP TABLE IF EXISTS `game_seat`;
CREATE TABLE `game_seat` (
  `game_id` int(11) NOT NULL default '0',
  `seat_id` int(11) NOT NULL default '0',
  `place` int(11) default NULL,
  `rating` float default NULL,
  `id` int(11) NOT NULL auto_increment,
  `seat_name` char(32) default NULL,
  PRIMARY KEY  (`id`),
  KEY `seat_id` (`seat_id`),
  KEY `game_id` (`game_id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `player`
--

DROP TABLE IF EXISTS `player`;
CREATE TABLE `player` (
  `jid` char(32) NOT NULL default '',
  `id` int(11) NOT NULL auto_increment,
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `player_attitude`
--

DROP TABLE IF EXISTS `player_attitude`;
CREATE TABLE `player_attitude` (
  `from_id` int(11) NOT NULL default '0',
  `to_id` int(11) NOT NULL default '0',
  `attitude` tinyint(4) default NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `player_seat`
--

DROP TABLE IF EXISTS `player_seat`;
CREATE TABLE `player_seat` (
  `id` int(11) NOT NULL auto_increment,
  `player_id` int(11) NOT NULL default '0',
  `seat_id` int(11) NOT NULL default '0',
  PRIMARY KEY  (`id`),
  KEY `player_id` (`player_id`),
  KEY `seat_id` (`seat_id`),
  KEY `seat_player` (`seat_id`,`player_id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `ruleset`
--

DROP TABLE IF EXISTS `ruleset`;
CREATE TABLE `ruleset` (
  `uri` varchar(64) NOT NULL default '',
  `name` varchar(64) default NULL,
  `description` text,
  `id` int(11) NOT NULL auto_increment,
  `homepage` varchar(256) default NULL,
  `player_id` int(11) default NULL,
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `seat`
--

DROP TABLE IF EXISTS `seat`;
CREATE TABLE `seat` (
  `id` int(11) NOT NULL auto_increment,
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `server`
--

DROP TABLE IF EXISTS `server`;
CREATE TABLE `server` (
  `public_key` text,
  `player_id` int(11) default NULL,
  `ruleset_id` int(11) NOT NULL default '0',
  `id` int(11) NOT NULL auto_increment,
  `jid` varchar(32) default NULL,
  `reputation` int(11) default '0',
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `server_attitude`
--

DROP TABLE IF EXISTS `server_attitude`;
CREATE TABLE `server_attitude` (
  `from_id` int(11) NOT NULL default '0',
  `to_id` int(11) NOT NULL default '0',
  `attitude` tinyint(4) default NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `ui_feature`
--

DROP TABLE IF EXISTS `ui_feature`;
CREATE TABLE `ui_feature` (
  `id` int(11) NOT NULL auto_increment,
  `uri` varchar(128) default NULL,
  `name` varchar(32) default NULL,
  `description` text,
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `ui_file`
--

DROP TABLE IF EXISTS `ui_file`;
CREATE TABLE `ui_file` (
  `id` int(11) NOT NULL auto_increment,
  `player_id` int(11) NOT NULL default '0',
  `name` varchar(64) NOT NULL default '',
  `description` text,
  `reputation` int(11) NOT NULL default '0',
  `url` varchar(128) NOT NULL default '',
  `ruleset_id` int(11) NOT NULL default '0',
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `ui_file_attitude`
--

DROP TABLE IF EXISTS `ui_file_attitude`;
CREATE TABLE `ui_file_attitude` (
  `player_id` int(11) default NULL,
  `ui_file_id` int(11) default NULL,
  `attitude` tinyint(4) default NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `ui_file_feature`
--

DROP TABLE IF EXISTS `ui_file_feature`;
CREATE TABLE `ui_file_feature` (
  `id` int(11) NOT NULL auto_increment,
  `ui_feature_id` int(11) default NULL,
  `ui_file_id` int(11) default NULL,
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;

--
-- Table structure for table `ui_file_language`
--

DROP TABLE IF EXISTS `ui_file_language`;
CREATE TABLE `ui_file_language` (
  `id` int(11) NOT NULL auto_increment,
  `ui_file_id` int(11) default NULL,
  `language_code` char(2) default NULL,
  PRIMARY KEY  (`id`)
) ENGINE=MyISAM DEFAULT CHARSET=latin1;


/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

