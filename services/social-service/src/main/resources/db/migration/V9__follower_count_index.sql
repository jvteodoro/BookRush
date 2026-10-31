-- Incoming follower counts use the second column of the existing composite PK.
CREATE INDEX social_follows_followed_idx ON social.follows(followed);
