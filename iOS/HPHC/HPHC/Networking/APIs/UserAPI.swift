//  Copyright 2020 Google LLC
//
//  Use of this source code is governed by an MIT-style
//  license that can be found in the LICENSE file or at
//  https://opensource.org/licenses/MIT.

import Foundation

struct UserAPI {

  /// Creates a request to reset the `User` password
  /// - Parameters:
  ///   - email: Email Id of the`User `
  ///   - completion: Request status handler
  static func forgotPassword(
    email: String,
    completion: @escaping StatusHandler
  ) {

    let params: JSONDictionary = [
      "email": email,
      "appId": AppConfiguration.appID,
    ]

    APIService.instance.requestForData(with: AuthRouter.forgotPassword(params: params)) { (_, status, error) in
      completion(status, error)
    }
  }

  /// Creates a request to logout the `User`
  /// - Parameters:
  ///   - completion: Request status handler
  static func logout(
    completion: @escaping StatusHandler
  ) {
    let userID = User.currentUser.userId ?? ""
    APIService.instance.requestForData(with: AuthRouter.logout(userID: userID)) { (_, status, error) in
      if status {
        DBHandler.logout()
      }
      completion(status, error)
    }
  }

}