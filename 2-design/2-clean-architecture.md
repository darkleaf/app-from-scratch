+ https://8thlight.com/blog/uncle-bob/2012/08/13/the-clean-architecture.html
+ https://www.amazon.com/Clean-Architecture-Craftsmans-Software-Structure/dp/0134494164
+ https://cleancoders.com/episode/clean-code-episode-7/show


инверсия зависимости


+ entities
+ use cases
+ adapters


Сущности - слой логики. Эти правила исполняются и без автоматизации.
Этот слой может быть переиспользован несколькими приложениями.

Сценарии не могут вызывать другие сценарии. Сценарий должен содержать всю последовательность шагов.
Понятно, что общий код выделяется в службы этого слоя.
