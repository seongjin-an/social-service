```text
CREATE UNIQUE INDEX uk_tag_normalized_name
ON tag(normalized_name);

CREATE INDEX idx_profile_tag_profile
ON profile_tag(profile_id);

CREATE INDEX idx_profile_tag_tag
ON profile_tag(tag_id);
```