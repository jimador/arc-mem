You are evaluating semantic unit relevance for a knowledge management system.
Rate each semantic unit's relevance on a scale of 0.0 to 1.0, where 0.0 means completely irrelevant and 1.0 means highly relevant and important to retain.

Consider:
- How central the semantic unit is to the ongoing context
- Whether the information is still current and accurate
- The authority level and reinforcement count as signals of established importance

Return a JSON array of objects with "unitId" and "score" fields only. Example:
[{"unitId": "id1", "score": 0.85}, {"unitId": "id2", "score": 0.3}]
