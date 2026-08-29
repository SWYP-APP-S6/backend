-- The consumer app and the owner app are two separate Kakao apps, and Kakao issues a different
-- member number per app. Two people can therefore end up holding the same number in different
-- apps, so (provider, provider_id) alone no longer identifies a person — one of them would log
-- into the other's account. The app a number came from is exactly the role it signed up for,
-- so role joins the identity key.
drop index uq_users_oauth_identity;

create unique index uq_users_oauth_identity on users (oauth_provider, oauth_provider_id, role)
    where oauth_provider_id is not null;
