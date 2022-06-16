namespace demo

structure User {
    @required
    id: UserId,

    @required
    name: Username,

    age: Integer,

    lastUpdatedAt: Timestamp
}

string UserId

string Username
